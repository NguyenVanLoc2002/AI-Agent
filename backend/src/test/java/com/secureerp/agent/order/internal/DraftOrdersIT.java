package com.secureerp.agent.order.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.TestcontainersConfiguration;
import com.secureerp.agent.erp.internal.MockErpConnector;
import com.secureerp.agent.erp.internal.MockErpOrderFault;
import com.secureerp.agent.erp.internal.MockErpSimulation;
import com.secureerp.agent.identity.IdentityFixtures;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.identity.spi.ErpCustomerLink;
import com.secureerp.agent.order.DraftOrderState;
import com.secureerp.agent.order.DraftOrderStateChanged;
import com.secureerp.agent.order.DraftOrderSubmission;
import com.secureerp.agent.order.OrderReconciliation.Finding;
import com.secureerp.agent.order.OrderReconciliation.UnknownDraftOrder;
import com.secureerp.agent.order.spi.DraftOrderCommand;
import com.secureerp.agent.order.spi.DraftOrderOutcome;
import com.secureerp.agent.order.spi.DraftOrderPort;
import com.secureerp.agent.order.spi.DraftOrderStatus;
import com.secureerp.agent.order.spi.ExternalRef;
import com.secureerp.agent.order.spi.IdempotencyKey;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.platform.Money;
import com.secureerp.agent.platform.MutableClock;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * EP-07 order module against PostgreSQL (Flyway V10) and the Mock ERP: one request per quote
 * version, one ERP call per request, unknown outcomes never sent again, both capability modes
 * (ADR-009 evidence: T-SEC04-PARALLEL, -DUPCALLBACK, -UNKNOWN at the order level; T-ERP-05) and
 * staff reconciliation with its audit trail.
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@Import(TestcontainersConfiguration.class)
class DraftOrdersIT {

    private static final Instant START = Instant.parse("2026-11-19T08:00:00Z");
    private static final String QUOTE = "BG-ABCDEFGH";
    private static final List<String> RESTRICTED = List.of("555444333", "987654321", "123456789", "SYNTH-CUST",
            "ZZHoney");

    @Autowired
    DraftOrderStore store;

    @Autowired
    DraftOrderPort port;

    @Autowired
    ErpCapabilities configuredCapabilities;

    @Autowired
    OrderProperties properties;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    MockErpConnector mockErp;

    @Autowired
    JdbcTemplate jdbc;

    private final MutableClock clock = new MutableClock(START);
    private MockErpSimulation erp;
    private DefaultDraftOrders orders;

    @BeforeEach
    void clean() {
        jdbc.update("delete from draft_order_event");
        jdbc.update("delete from draft_order_request");
        jdbc.update("delete from quote where public_ref = ?", QUOTE);
        erp = mockErp.simulation();
        erp.reset();
        clock.set(START);
        orders = service(configuredCapabilities);
    }

    private DefaultDraftOrders service(ErpCapabilities capabilities) {
        return new DefaultDraftOrders(store, port, capabilities, properties, transactions, events, clock);
    }

    private DefaultDraftOrders service(ErpCapabilities capabilities, DraftOrderPort erpPort,
            ApplicationEventPublisher publisher) {
        return new DefaultDraftOrders(store, erpPort, capabilities, properties, transactions, publisher, clock);
    }

    private void register(DefaultDraftOrders service, String quoteRef, int version) {
        transactions.executeWithoutResult(tx -> service.register(submission(quoteRef, version)));
    }

    private static DraftOrderSubmission submission(String quoteRef, int version) {
        return new DraftOrderSubmission(quoteRef, version, new ErpCustomerLink("SYNTH-CUST-A"),
                List.of(new DraftOrderSubmission.Line("SKU-BRK-PAD-A", 2,
                        Money.of(new BigDecimal("555444333"), Currency.getInstance("VND")))));
    }

    private static SessionIdentity sales() {
        return IdentityFixtures.staffIdentity("sales1", Role.SALES);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private List<String> eventsOf(String quoteRef) {
        return jdbc.queryForList("select e.event from draft_order_event e join draft_order_request r "
                + "on r.id = e.request_id where r.quote_ref = ? order by e.id", String.class, quoteRef);
    }

    /** T-ERP-05: the safe default comes from configuration, and it is "supports nothing". */
    @Test
    void capabilitiesDefaultToUnsupported() {
        assertThat(configuredCapabilities).isEqualTo(new ErpCapabilities(false, false));
    }

    @Test
    void transactionBoundariesAreExplicit() {
        assertThatThrownBy(() -> orders.register(submission(QUOTE, 1))).isInstanceOf(IllegalStateException.class);
        register(orders, QUOTE, 1);
        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> orders.dispatch(submission(QUOTE, 1))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> orders.dispatch(submission("BG-ZZZZZZZZ", 1)))
                .as("never sends a request that was not registered").isInstanceOf(IllegalStateException.class);
        assertThat(erp.createCalls()).isZero();
    }

    /** BR-17: one request per quote version, enforced by the database, key derived on the server. */
    @Test
    void aQuoteVersionHasOneRequestWithAServerDerivedKey() {
        register(orders, QUOTE, 1);
        register(orders, QUOTE, 1);
        register(orders, QUOTE, 2);

        assertThat(jdbc.queryForList("select idempotency_key from draft_order_request order by quote_version",
                String.class)).containsExactly("BG-ABCDEFGH:1", "BG-ABCDEFGH:2");
        assertThatThrownBy(() -> jdbc.update("insert into draft_order_request (quote_ref, quote_version, "
                + "idempotency_key, payload_digest, status, created_at) values (?, 1, 'BG-ABCDEFGH:9', ?, 'PENDING', "
                + "now())", QUOTE, submission(QUOTE, 1).digest()))
                .isInstanceOf(DataIntegrityViolationException.class);
        // A request counted as created must name the ERP draft (database check).
        assertThatThrownBy(() -> jdbc.update("update draft_order_request set status = 'CREATED' where quote_version = 2"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(orders.stateOf(QUOTE, 1)).contains(DraftOrderState.PENDING);
        assertThat(orders.stateOf(QUOTE, 3)).isEmpty();
    }

    /** Order sends only the order that was confirmed: another customer, quantity or price is refused. */
    @Test
    void aSubmissionOtherThanTheRegisteredOneIsNeverSent() {
        register(orders, QUOTE, 1);
        Money price = Money.of(new BigDecimal("555444333"), Currency.getInstance("VND"));
        List<DraftOrderSubmission> tampered = List.of(
                new DraftOrderSubmission(QUOTE, 1, new ErpCustomerLink("SYNTH-CUST-B"),
                        List.of(new DraftOrderSubmission.Line("SKU-BRK-PAD-A", 2, price))),
                new DraftOrderSubmission(QUOTE, 1, new ErpCustomerLink("SYNTH-CUST-A"),
                        List.of(new DraftOrderSubmission.Line("SKU-BRK-PAD-A", 3, price))),
                new DraftOrderSubmission(QUOTE, 1, new ErpCustomerLink("SYNTH-CUST-A"),
                        List.of(new DraftOrderSubmission.Line("SKU-BRK-PAD-A", 2,
                                Money.of(BigDecimal.ONE, Currency.getInstance("VND"))))),
                new DraftOrderSubmission(QUOTE, 1, new ErpCustomerLink("SYNTH-CUST-A"),
                        List.of(new DraftOrderSubmission.Line("SKU-AIR-FLT-A", 2, price))));
        for (DraftOrderSubmission other : tampered) {
            assertThatThrownBy(() -> orders.dispatch(other)).isInstanceOf(IllegalStateException.class);
        }
        assertThat(erp.createCalls()).isZero();
        assertThat(orders.stateOf(QUOTE, 1)).contains(DraftOrderState.PENDING);

        // The same amount at another scale is the same order.
        assertThat(orders.dispatch(new DraftOrderSubmission(QUOTE, 1, new ErpCustomerLink("SYNTH-CUST-A"),
                List.of(new DraftOrderSubmission.Line("SKU-BRK-PAD-A", 2,
                        Money.of(new BigDecimal("555444333.0000"), Currency.getInstance("VND")))))))
                .isEqualTo(DraftOrderState.CREATED);
        assertThat(erp.createCalls()).isEqualTo(1);
        // Once sent, a caller holding something else (e.g. after the customer was re-linked) just reads the state.
        assertThat(orders.dispatch(tampered.getFirst())).isEqualTo(DraftOrderState.CREATED);
        assertThat(erp.createCalls()).isEqualTo(1);
    }

    /**
     * Reviews 2026-11-19 (security M2, backend M2): the ERP answers after the request was already counted
     * as unknown. The request stays unknown (staff decide), but the ERP's draft number is kept for them.
     */
    @Test
    void anAnswerAfterTheTimeoutIsKeptAsEvidenceAndChangesNoState() {
        DraftOrderPort slowErp = new DraftOrderPort() {
            @Override
            public DraftOrderOutcome create(DraftOrderCommand command, Optional<IdempotencyKey> idempotencyKey) {
                clock.advance(properties.submitTimeout().plusSeconds(1));
                assertThat(orders.stateOf(QUOTE, 1)).contains(DraftOrderState.OUTCOME_UNKNOWN); // another reader sweeps
                return new DraftOrderOutcome.Created("MOCK-DO-000777");
            }

            @Override
            public Optional<DraftOrderStatus> findByExternalRef(ExternalRef externalRef) {
                throw new AssertionError("no lookup without the capability");
            }
        };
        DefaultDraftOrders slow = service(configuredCapabilities, slowErp, events);
        register(slow, QUOTE, 1);

        assertThat(slow.dispatch(submission(QUOTE, 1))).isEqualTo(DraftOrderState.OUTCOME_UNKNOWN);

        assertThat(jdbc.queryForObject("select status || ':' || late_erp_order_ref || ':' || "
                + "coalesce(erp_order_ref, '-') from draft_order_request", String.class))
                .isEqualTo("OUTCOME_UNKNOWN:MOCK-DO-000777:-");
        assertThat(eventsOf(QUOTE)).containsExactly("REGISTERED", "SUBMITTED", "TIMED_OUT", "LATE_ANSWER_CREATED");
        // Review round 2: staff see what the ERP reported, and cannot decide against it.
        UnknownDraftOrder waiting = orders.unknown(sales()).getFirst();
        assertThat(waiting.reportedErpOrderRef()).isEqualTo("MOCK-DO-000777");
        assertCode(() -> orders.reconcile(sales(), waiting.requestId(), Finding.ABSENT, null), ErrorCode.CONFLICT);
        assertCode(() -> orders.reconcile(sales(), waiting.requestId(), Finding.CREATED, "MOCK-DO-000001"),
                ErrorCode.CONFLICT);
        assertThat(orders.stateOf(QUOTE, 1)).contains(DraftOrderState.OUTCOME_UNKNOWN);
        assertThat(orders.reconcile(sales(), waiting.requestId(), Finding.CREATED, "MOCK-DO-000777"))
                .isEqualTo(DraftOrderState.RECONCILED_CREATED);
    }

    /**
     * Review round 2 (security M1, backend M1): staff decided "absent" before the slow ERP call returned
     * a draft. The decision stands (no state change by a late answer), but the answer is stored with an
     * event, so the contradiction is on record.
     */
    @Test
    void anAnswerAfterStaffReconciledIsStillRecorded(CapturedOutput output) {
        DraftOrderPort slowErp = new DraftOrderPort() {
            @Override
            public DraftOrderOutcome create(DraftOrderCommand command, Optional<IdempotencyKey> idempotencyKey) {
                clock.advance(properties.submitTimeout().plusSeconds(1));
                long id = orders.unknown(sales()).getFirst().requestId();
                orders.reconcile(sales(), id, Finding.ABSENT, null);
                return new DraftOrderOutcome.Created("MOCK-DO-000888");
            }

            @Override
            public Optional<DraftOrderStatus> findByExternalRef(ExternalRef externalRef) {
                throw new AssertionError("no lookup without the capability");
            }
        };
        DefaultDraftOrders slow = service(configuredCapabilities, slowErp, events);
        register(slow, QUOTE, 1);

        assertThat(slow.dispatch(submission(QUOTE, 1))).isEqualTo(DraftOrderState.RECONCILED_ABSENT);

        assertThat(jdbc.queryForObject("select status || ':' || late_erp_order_ref || ':' || "
                + "coalesce(erp_order_ref, '-') from draft_order_request", String.class))
                .isEqualTo("RECONCILED_ABSENT:MOCK-DO-000888:-");
        assertThat(eventsOf(QUOTE)).containsExactly("REGISTERED", "SUBMITTED", "TIMED_OUT", "RECONCILED_ABSENT",
                "LATE_ANSWER_CREATED");
        assertThat(output.getAll()).contains("late_answer_contradicts_reconciliation").doesNotContain("MOCK-DO-000888");
    }

    /**
     * Reviews (architecture H3, round 2 M1): when the quote cannot follow the request, the reconciliation
     * is rolled back and answered as a conflict; request and quote never drift apart silently.
     */
    @Test
    void aReconciliationTheQuoteCannotFollowIsRolledBack() {
        register(orders, QUOTE, 1);
        erp.failOrders(MockErpOrderFault.TIMEOUT_AFTER_COMMIT);
        orders.dispatch(submission(QUOTE, 1));
        long id = orders.unknown(sales()).getFirst().requestId();
        // A quote in a state no draft order outcome can follow (it never was confirmed).
        jdbc.update("insert into quote (public_ref, conversation_id, customer_digest, version, status, currency, "
                + "money_scale, rounding_mode, total, issued_at, valid_until) values (?, 'cv_ConvMirrorZZZZZZZZZZZZ', "
                + "?, 1, 'EXPIRED', 'VND', 0, 'HALF_UP', 0, ?, ?)", QUOTE, "0".repeat(64),
                Timestamp.from(START), Timestamp.from(START.plusSeconds(60)));

        assertCode(() -> orders.reconcile(sales(), id, Finding.CREATED, "MOCK-DO-000001"), ErrorCode.CONFLICT);

        assertThat(orders.stateOf(QUOTE, 1)).contains(DraftOrderState.OUTCOME_UNKNOWN);
        assertThat(eventsOf(QUOTE)).doesNotContain("RECONCILED_CREATED");
        assertThat(jdbc.queryForObject("select status from quote where public_ref = ?", String.class, QUOTE))
                .isEqualTo("EXPIRED");
    }

    /**
     * Reviews 2026-11-19 (architecture H2, backend M1, security L2): each timed-out request is handled on
     * its own. A listener failing for one request neither blocks the others nor the caller.
     */
    @Test
    void oneRequestThatCannotBeTimedOutDoesNotBlockTheOthers() {
        String poisoned = "BG-PPPPPPPP";
        ApplicationEventPublisher failingForOne = event -> {
            if (event instanceof DraftOrderStateChanged change && change.quoteRef().equals(poisoned)) {
                throw new IllegalStateException("listener failed");
            }
            events.publishEvent(event);
        };
        DefaultDraftOrders sweeping = service(configuredCapabilities, port, failingForOne);
        register(sweeping, poisoned, 1);
        register(sweeping, QUOTE, 1);
        register(sweeping, QUOTE, 2);
        clock.advance(properties.submitTimeout());

        assertThat(sweeping.unknown(sales())).extracting(UnknownDraftOrder::quoteRef).containsExactly(QUOTE, QUOTE);

        assertThat(jdbc.queryForObject("select status from draft_order_request where quote_ref = ?", String.class,
                poisoned)).as("rolled back with its failed listener, retried by the next sweep").isEqualTo("PENDING");
        assertThat(eventsOf(poisoned)).containsExactly("REGISTERED");
        // Once the listener works again the request is picked up, and it is never sent.
        assertThat(orders.unknown(sales())).hasSize(3);
        assertThat(erp.createCalls()).isZero();
    }

    /** T-SEC04-DUPCALLBACK at the order level: a repeated dispatch reads the state and writes nothing. */
    @Test
    void aRequestIsSentOnceAndRepeatsOnlyReadItsState(CapturedOutput output) {
        register(orders, QUOTE, 1);

        assertThat(orders.dispatch(submission(QUOTE, 1))).isEqualTo(DraftOrderState.CREATED);
        assertThat(orders.dispatch(submission(QUOTE, 1))).isEqualTo(DraftOrderState.CREATED);
        register(orders, QUOTE, 1);
        assertThat(orders.dispatch(submission(QUOTE, 1))).isEqualTo(DraftOrderState.CREATED);

        assertThat(erp.createCalls()).isEqualTo(1);
        assertThat(erp.draftCount()).isEqualTo(1);
        assertThat(erp.draftExternalRefs()).containsExactly("BG-ABCDEFGH:1");
        assertThat(jdbc.queryForObject("select erp_order_ref from draft_order_request", String.class))
                .isEqualTo("MOCK-DO-000001");
        assertThat(eventsOf(QUOTE)).containsExactly("REGISTERED", "SUBMITTED", "CREATED");
        // BR-18: the key stays here while the capability is off.
        assertThat(erp.draftsWithIdempotencyKey()).isZero();
        assertThat(count("select count(*) from draft_order_request where key_forwarded")).isZero();
        assertNothingRestricted(output);
    }

    /** T-SEC04-PARALLEL at the order level: ten callers, one conditional update wins, one ERP call. */
    @Test
    void tenParallelDispatchesReachTheErpOnce() throws Exception {
        register(orders, QUOTE, 1);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<DraftOrderState>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 10; i++) {
                Callable<DraftOrderState> call = () -> {
                    start.await();
                    return orders.dispatch(submission(QUOTE, 1));
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            for (Future<DraftOrderState> result : results) {
                assertThat(result.get()).isIn(DraftOrderState.SUBMITTED, DraftOrderState.CREATED);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(erp.createCalls()).isEqualTo(1);
        assertThat(erp.draftCount()).isEqualTo(1);
        assertThat(count("select count(*) from draft_order_request")).isEqualTo(1);
        assertThat(orders.stateOf(QUOTE, 1)).contains(DraftOrderState.CREATED);
    }

    /**
     * T-SEC04-UNKNOWN + T-ERP-05 (capabilities false): the ERP wrote the draft but the answer was lost.
     * The request is OUTCOME_UNKNOWN, nothing is sent again even when the ERP is healthy, no lookup is
     * made, and the request waits in the staff queue.
     */
    @Test
    void aTimeoutAfterTheErpWroteIsUnknownAndNeverSentAgain(CapturedOutput output) {
        register(orders, QUOTE, 1);
        erp.failOrders(MockErpOrderFault.TIMEOUT_AFTER_COMMIT);

        assertThat(orders.dispatch(submission(QUOTE, 1))).isEqualTo(DraftOrderState.OUTCOME_UNKNOWN);
        erp.failOrders(MockErpOrderFault.NONE);
        assertThat(orders.dispatch(submission(QUOTE, 1))).isEqualTo(DraftOrderState.OUTCOME_UNKNOWN);
        register(orders, QUOTE, 1);
        assertThat(orders.dispatch(submission(QUOTE, 1))).isEqualTo(DraftOrderState.OUTCOME_UNKNOWN);

        assertThat(erp.createCalls()).as("no blind retry").isEqualTo(1);
        assertThat(erp.draftCount()).isEqualTo(1);
        assertThat(erp.lookupCalls()).as("no lookup without the capability").isZero();
        assertThat(jdbc.queryForObject("select detail_code from draft_order_request", String.class))
                .isEqualTo("TIMEOUT");
        assertThat(orders.unknown(sales())).extracting(UnknownDraftOrder::externalRef)
                .containsExactly("BG-ABCDEFGH:1");
        assertNothingRestricted(output);
    }

    @Test
    void aRejectionIsFinalAndAnUnclassifiedErrorIsUnknown() {
        register(orders, QUOTE, 1);
        erp.failOrders(MockErpOrderFault.REJECT);
        assertThat(orders.dispatch(submission(QUOTE, 1))).isEqualTo(DraftOrderState.REJECTED);
        assertThat(jdbc.queryForObject("select detail_code from draft_order_request where quote_version = 1",
                String.class)).isEqualTo("CUSTOMER_NOT_ALLOWED");

        register(orders, QUOTE, 2);
        erp.failOrders(MockErpOrderFault.ERROR_WITH_SENTINEL);
        assertThat(orders.dispatch(submission(QUOTE, 2))).isEqualTo(DraftOrderState.OUTCOME_UNKNOWN);

        erp.failOrders(MockErpOrderFault.NONE);
        assertThat(orders.dispatch(submission(QUOTE, 1))).isEqualTo(DraftOrderState.REJECTED);
        assertThat(erp.createCalls()).isEqualTo(2);
        assertThat(erp.draftCount()).isZero();
        assertThat(orders.unknown(sales())).hasSize(1);
    }

    /** ADR-009, capabilities true: the key is forwarded and one lookup by our reference reconciles. */
    @Test
    void withTheCapabilitiesTheKeyIsForwardedAndALookupReconciles() {
        DefaultDraftOrders capable = service(new ErpCapabilities(true, true));
        register(capable, QUOTE, 1);
        erp.failOrders(MockErpOrderFault.TIMEOUT_AFTER_COMMIT);

        assertThat(capable.dispatch(submission(QUOTE, 1))).isEqualTo(DraftOrderState.RECONCILED_CREATED);

        assertThat(erp.createCalls()).isEqualTo(1);
        assertThat(erp.lookupCalls()).isEqualTo(1);
        assertThat(erp.draftsWithIdempotencyKey()).isEqualTo(1);
        assertThat(count("select count(*) from draft_order_request where key_forwarded")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select erp_order_ref from draft_order_request", String.class))
                .isEqualTo("MOCK-DO-000001");
        assertThat(eventsOf(QUOTE)).containsExactly("REGISTERED", "SUBMITTED", "OUTCOME_UNKNOWN",
                "RECONCILED_BY_LOOKUP");
        assertThat(capable.unknown(sales())).isEmpty();
    }

    /** Not finding the draft right after a timeout proves nothing: it stays unknown, for staff. */
    @Test
    void aLookupThatFindsNothingLeavesTheRequestUnknown() {
        DefaultDraftOrders capable = service(new ErpCapabilities(true, true));
        register(capable, QUOTE, 1);
        erp.failOrders(MockErpOrderFault.TIMEOUT_BEFORE_COMMIT);

        assertThat(capable.dispatch(submission(QUOTE, 1))).isEqualTo(DraftOrderState.OUTCOME_UNKNOWN);
        assertThat(capable.dispatch(submission(QUOTE, 1))).isEqualTo(DraftOrderState.OUTCOME_UNKNOWN);

        assertThat(erp.createCalls()).isEqualTo(1);
        assertThat(erp.lookupCalls()).isEqualTo(1);
        assertThat(erp.draftCount()).isZero();
        assertThat(capable.unknown(sales())).hasSize(1);
    }

    /** A request never sent, or sent without a recorded answer, becomes unknown after the timeout. */
    @Test
    void anUnfinishedRequestBecomesUnknownAfterTheTimeoutAndIsNotSentLate() {
        register(orders, QUOTE, 1);
        register(orders, QUOTE, 2);
        transactions.executeWithoutResult(tx ->
                store.claim(QUOTE, 2, false, START.minus(Duration.ofMinutes(1)), START));
        clock.advance(properties.submitTimeout().minusSeconds(1));
        assertThat(orders.stateOf(QUOTE, 1)).contains(DraftOrderState.PENDING);
        assertThat(orders.stateOf(QUOTE, 2)).contains(DraftOrderState.SUBMITTED);

        clock.advance(Duration.ofSeconds(1));
        assertThat(orders.dispatch(submission(QUOTE, 1))).isEqualTo(DraftOrderState.OUTCOME_UNKNOWN);
        assertThat(orders.stateOf(QUOTE, 2)).contains(DraftOrderState.OUTCOME_UNKNOWN);

        assertThat(erp.createCalls()).isZero();
        assertThat(jdbc.queryForList("select distinct detail_code from draft_order_request", String.class))
                .containsExactly("NO_RECORDED_ANSWER");
        assertThat(eventsOf(QUOTE)).contains("TIMED_OUT");
    }

    /** RBAC_MATRIX: only SALES handles an unknown outcome; an operator is not a business reader. */
    @Test
    void onlySalesListsAndReconciles() {
        register(orders, QUOTE, 1);
        erp.failOrders(MockErpOrderFault.TIMEOUT_AFTER_COMMIT);
        orders.dispatch(submission(QUOTE, 1));
        long id = orders.unknown(sales()).getFirst().requestId();

        for (SessionIdentity other : List.of(IdentityFixtures.staffIdentity("ops1", Role.OPS),
                IdentityFixtures.staffIdentity("secrev1", Role.SECREV),
                IdentityFixtures.sessionIdentity("custA", "SynthCustomerA"))) {
            assertThatThrownBy(() -> orders.unknown(other)).isInstanceOfSatisfying(ApplicationException.class,
                    e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
            assertThatThrownBy(() -> orders.reconcile(other, id, Finding.ABSENT, null))
                    .isInstanceOfSatisfying(ApplicationException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
        }
        assertThat(orders.stateOf(QUOTE, 1)).contains(DraftOrderState.OUTCOME_UNKNOWN);
    }

    /** SEC-04 reconciliation evidence: the decision, its actor and its time are stored; nothing is sent. */
    @Test
    void reconciliationRecordsTheDecisionAndItsActor() {
        register(orders, QUOTE, 1);
        register(orders, QUOTE, 2);
        erp.failOrders(MockErpOrderFault.TIMEOUT_AFTER_COMMIT);
        orders.dispatch(submission(QUOTE, 1));
        erp.failOrders(MockErpOrderFault.TIMEOUT_BEFORE_COMMIT);
        orders.dispatch(submission(QUOTE, 2));
        List<UnknownDraftOrder> unknown = orders.unknown(sales());
        assertThat(unknown).extracting(UnknownDraftOrder::quoteVersion).containsExactly(1, 2);
        long first = unknown.get(0).requestId();
        long second = unknown.get(1).requestId();

        // A decision must say what was found: CREATED needs the ERP draft number, ABSENT must not have one.
        assertCode(() -> orders.reconcile(sales(), first, Finding.CREATED, null), ErrorCode.VALIDATION_FAILED);
        assertCode(() -> orders.reconcile(sales(), first, Finding.ABSENT, "MOCK-DO-000001"),
                ErrorCode.VALIDATION_FAILED);
        assertCode(() -> orders.reconcile(sales(), first, Finding.CREATED, "x y; drop"), ErrorCode.VALIDATION_FAILED);
        assertCode(() -> orders.reconcile(sales(), 999_999L, Finding.ABSENT, null), ErrorCode.NOT_FOUND);

        assertThat(orders.reconcile(sales(), first, Finding.CREATED, "MOCK-DO-000001"))
                .isEqualTo(DraftOrderState.RECONCILED_CREATED);
        assertThat(orders.reconcile(sales(), second, Finding.ABSENT, null))
                .isEqualTo(DraftOrderState.RECONCILED_ABSENT);
        // The same decision again is accepted; a different one is refused.
        assertThat(orders.reconcile(sales(), first, Finding.CREATED, "MOCK-DO-000001"))
                .isEqualTo(DraftOrderState.RECONCILED_CREATED);
        assertCode(() -> orders.reconcile(sales(), first, Finding.ABSENT, null), ErrorCode.CONFLICT);
        assertCode(() -> orders.reconcile(sales(), first, Finding.CREATED, "MOCK-DO-000002"), ErrorCode.CONFLICT);

        assertThat(orders.unknown(sales())).isEmpty();
        assertThat(jdbc.queryForList("select e.event || ':' || coalesce(e.actor, '-') from draft_order_event e "
                + "where e.event like 'RECONCILED%' order by e.id", String.class)).containsExactly(
                "RECONCILED_CREATED:" + sales().principal().pseudonym(),
                "RECONCILED_ABSENT:" + sales().principal().pseudonym());
        assertThat(erp.createCalls()).as("reconciliation never writes to the ERP").isEqualTo(2);
        // A settled request cannot be reconciled.
        register(orders, "BG-ZZZZZZZZ", 1);
        erp.failOrders(MockErpOrderFault.NONE);
        orders.dispatch(submission("BG-ZZZZZZZZ", 1));
        long created = jdbc.queryForObject("select id from draft_order_request where quote_ref = 'BG-ZZZZZZZZ'",
                Long.class);
        assertCode(() -> orders.reconcile(sales(), created, Finding.ABSENT, null), ErrorCode.CONFLICT);
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApplicationException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    private void assertNothingRestricted(CapturedOutput output) {
        String stored = jdbc.queryForList("select row_to_json(r)::text from draft_order_request r", String.class)
                + jdbc.queryForList("select row_to_json(e)::text from draft_order_event e", String.class).toString();
        for (String value : RESTRICTED) {
            assertThat(output.getAll()).doesNotContain(value);
            assertThat(stored).doesNotContain(value);
        }
        assertThat(output.getAll()).doesNotContain(QUOTE);
    }
}
