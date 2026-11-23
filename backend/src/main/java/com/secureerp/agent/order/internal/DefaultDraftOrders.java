package com.secureerp.agent.order.internal;

import com.secureerp.agent.audit.AuditActor;
import com.secureerp.agent.audit.AuditEvent;
import com.secureerp.agent.audit.AuditTrail;
import com.secureerp.agent.audit.DeniedInTransaction;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.identity.Team;
import com.secureerp.agent.order.DraftOrderMirrorRefused;
import com.secureerp.agent.order.DraftOrderState;
import com.secureerp.agent.order.DraftOrderStateChanged;
import com.secureerp.agent.order.DraftOrderSubmission;
import com.secureerp.agent.order.DraftOrders;
import com.secureerp.agent.order.OrderReconciliation;
import com.secureerp.agent.order.spi.DraftOrderCommand;
import com.secureerp.agent.order.spi.DraftOrderOutcome;
import com.secureerp.agent.order.spi.DraftOrderPort;
import com.secureerp.agent.order.spi.DraftOrderStatus;
import com.secureerp.agent.order.spi.ExternalRef;
import com.secureerp.agent.order.spi.IdempotencyKey;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Draft orders with an outbox row per quote version (ADR-009 ACCEPTED 2026-11-19; rule 20).
 *
 * <ol>
 *   <li>{@link #register} writes the PENDING row in the confirming transaction (UNIQUE quote reference
 *       and version, BR-17).</li>
 *   <li>{@link #dispatch} claims it (PENDING → SUBMITTED, a conditional update only one caller can
 *       win), calls the ERP once outside any transaction and records the answer. Only the submission
 *       whose digest was registered is sent. The idempotency key is forwarded only when
 *       {@link ErpCapabilities} says so (BR-18).</li>
 *   <li>A timeout, an unclassifiable answer, a failure to record the answer or a request left
 *       unfinished past {@code apas.order.submit-timeout} is OUTCOME_UNKNOWN. Nothing is ever sent a
 *       second time (BR-19). With the lookup capability the ERP is asked once by our reference;
 *       otherwise, and when the lookup finds nothing, staff reconcile by hand. An answer that arrives
 *       after the request was counted as unknown is kept as evidence and changes nothing else.</li>
 * </ol>
 * Logs carry states and codes only: never a reference, a customer key or an amount.
 */
@Service
class DefaultDraftOrders implements DraftOrders, OrderReconciliation {

    private static final Logger log = LoggerFactory.getLogger(DefaultDraftOrders.class);
    static final int MAX_LISTED = 50;
    static final int MAX_SWEPT = 100;
    /**
     * Pages of {@link #MAX_SWEPT} the background sweep reads, by key, in one run: requests that cannot be expired
     * (their transaction keeps failing) no longer starve newer ones (EP-09c reviews, backend M1, security L-3).
     * A read path sweeps one page only, so a customer request stays short.
     */
    static final int BACKGROUND_PAGES = 10;
    static final int READ_PATH_PAGES = 1;

    private final DraftOrderStore store;
    private final DraftOrderPort erp;
    private final ErpCapabilities capabilities;
    private final OrderProperties properties;
    private final TransactionTemplate transactions;
    private final TransactionTemplate ownTransaction;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AuditTrail audit;

    DefaultDraftOrders(DraftOrderStore store, DraftOrderPort erp, ErpCapabilities capabilities,
            OrderProperties properties, TransactionTemplate transactions, ApplicationEventPublisher events,
            Clock clock, AuditTrail audit) {
        this.audit = audit;
        this.store = store;
        this.erp = erp;
        this.capabilities = capabilities;
        this.properties = properties;
        this.transactions = transactions;
        // EP-09c round-2 review (backend M2): each expiry has a transaction of its own, so a failing request
        // never marks a caller's transaction rollback-only, whoever calls the sweep.
        this.ownTransaction = new TransactionTemplate(Objects.requireNonNull(transactions.getTransactionManager()));
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.events = events;
        this.clock = clock;
    }

    static String key(String quoteRef, int quoteVersion) {
        return quoteRef + ":" + quoteVersion;
    }

    @Override
    public void register(DraftOrderSubmission submission, Team team) {
        Objects.requireNonNull(submission, "submission");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("register must run in the confirming transaction");
        }
        Instant now = clock.instant();
        store.insertPending(submission.quoteRef(), submission.quoteVersion(),
                        key(submission.quoteRef(), submission.quoteVersion()), submission.digest(),
                        team == null ? null : team.value(), now)
                .ifPresent(id -> store.event(id, "REGISTERED", null, now));
    }

    @Override
    public DraftOrderState dispatch(DraftOrderSubmission submission) {
        Objects.requireNonNull(submission, "submission");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("dispatch must not run inside a transaction");
        }
        String quoteRef = submission.quoteRef();
        int version = submission.quoteVersion();
        Instant now = clock.instant();
        boolean forwardKey = capabilities.supportsIdempotencyKey();
        DraftOrderStore.Request registered = store.find(quoteRef, version)
                .orElseThrow(() -> new IllegalStateException("draft order request was not registered"));
        if (registered.state() != DraftOrderState.PENDING) {
            // Already sent or settled: nothing to send, whatever the caller holds now.
            return stateOf(quoteRef, version).orElse(registered.state());
        }
        if (!registered.payloadDigest().equals(submission.digest())) {
            // Never send anything but the order the customer confirmed.
            log.error("order.dispatch refused code=SUBMISSION_MISMATCH");
            throw new IllegalStateException("submission does not match the registered draft order request");
        }
        Optional<Long> claimed = transactions.execute(tx -> {
            Optional<Long> id = store.claim(quoteRef, version, forwardKey, now.minus(properties.submitTimeout()), now);
            id.ifPresent(request -> store.event(request, "SUBMITTED", null, now));
            return id;
        });
        if (claimed == null || claimed.isEmpty()) {
            return stateOf(quoteRef, version)
                    .orElseThrow(() -> new IllegalStateException("draft order request was not registered"));
        }
        long id = claimed.get();
        String key = key(quoteRef, version);
        DraftOrderOutcome outcome = callErp(submission, key, forwardKey);
        DraftOrderState state = record(id, outcome);
        log.info("order.dispatch state={} keyForwarded={}", state, forwardKey);
        if (state == DraftOrderState.OUTCOME_UNKNOWN && capabilities.supportsExternalRefLookup()) {
            return lookUp(id, new ExternalRef(key));
        }
        return state;
    }

    /** One call, no retry. Whatever the adapter throws is an unknown outcome: the ERP may have written. */
    private DraftOrderOutcome callErp(DraftOrderSubmission submission, String key, boolean forwardKey) {
        try {
            DraftOrderCommand command = new DraftOrderCommand(submission.customer(), new ExternalRef(key),
                    submission.lines().stream()
                            .map(line -> new DraftOrderCommand.Line(line.sku(), line.quantity(), line.unitPrice()))
                            .toList());
            DraftOrderOutcome outcome =
                    erp.create(command, forwardKey ? Optional.of(new IdempotencyKey(key)) : Optional.empty());
            return Objects.requireNonNull(outcome, "outcome");
        } catch (RuntimeException failure) {
            log.warn("order.dispatch erp_call_failed exception={}", failure.getClass().getSimpleName());
            return new DraftOrderOutcome.Unknown(DraftOrderOutcome.UnknownReason.UNCLASSIFIED);
        }
    }

    /**
     * Stores the answer. If this write fails the exception propagates and the request stays
     * SUBMITTED, which becomes OUTCOME_UNKNOWN after the submit timeout; it is never sent again.
     */
    private DraftOrderState record(long id, DraftOrderOutcome outcome) {
        Instant now = clock.instant();
        DraftOrderState to;
        String erpOrderRef = null;
        String detail = null;
        switch (outcome) {
            case DraftOrderOutcome.Created created -> {
                to = DraftOrderState.CREATED;
                erpOrderRef = created.erpOrderRef();
            }
            case DraftOrderOutcome.Rejected rejected -> {
                to = DraftOrderState.REJECTED;
                detail = rejected.code().name();
            }
            case DraftOrderOutcome.Unknown unknown -> {
                to = DraftOrderState.OUTCOME_UNKNOWN;
                detail = unknown.reason().name();
            }
        }
        DraftOrderState target = to;
        String ref = erpOrderRef;
        String code = detail;
        Boolean changed = transactions.execute(tx -> {
            boolean done = store.transition(id, DraftOrderState.SUBMITTED, target, ref, code, now);
            if (done) {
                store.event(id, target.name(), null, now);
            }
            return done;
        });
        if (Boolean.TRUE.equals(changed)) {
            return target;
        }
        // The request was already counted as unknown while the ERP answered late. It stays unknown, for
        // staff, but what the ERP said is kept as evidence for the reconciliation.
        return lateAnswer(id, target, ref, code, now);
    }

    /**
     * The ERP answered after the request had left SUBMITTED. The answer is always stored with an event,
     * also when staff reconciled in the meantime; an answer that contradicts their decision is logged
     * as an error for follow-up (A-41(w)).
     */
    private DraftOrderState lateAnswer(long id, DraftOrderState answer, String erpOrderRef, String detailCode,
            Instant now) {
        transactions.executeWithoutResult(tx -> {
            store.recordLateAnswer(id, erpOrderRef, detailCode);
            store.event(id, "LATE_ANSWER_" + answer.name(), null, now);
        });
        DraftOrderState state = store.findById(id).map(DraftOrderStore.Request::state)
                .orElse(DraftOrderState.OUTCOME_UNKNOWN);
        if (state == DraftOrderState.RECONCILED_ABSENT && erpOrderRef != null) {
            log.error("order.dispatch late_answer_contradicts_reconciliation");
        } else {
            log.warn("order.dispatch answer_after_timeout answer={}", answer);
        }
        return state;
    }

    /** One lookup by our reference (capability only). Found → RECONCILED_CREATED; anything else stays for staff. */
    private DraftOrderState lookUp(long id, ExternalRef externalRef) {
        Optional<DraftOrderStatus> found;
        try {
            found = erp.findByExternalRef(externalRef);
        } catch (RuntimeException failure) {
            log.warn("order.lookup failed exception={}", failure.getClass().getSimpleName());
            return DraftOrderState.OUTCOME_UNKNOWN;
        }
        if (found == null || found.isEmpty()) {
            // Absence right after a timeout proves nothing: the write may still land.
            log.info("order.lookup outcome=NOT_FOUND");
            return DraftOrderState.OUTCOME_UNKNOWN;
        }
        Instant now = clock.instant();
        String erpOrderRef = found.get().erpOrderRef();
        Boolean changed = transactions.execute(tx -> {
            boolean done = store.transition(id, DraftOrderState.OUTCOME_UNKNOWN, DraftOrderState.RECONCILED_CREATED,
                    erpOrderRef, null, now);
            if (done) {
                store.event(id, "RECONCILED_BY_LOOKUP", null, now);
            }
            return done;
        });
        log.info("order.lookup outcome=FOUND");
        // Staff decided first: keep what the lookup found as evidence next to their decision.
        return Boolean.TRUE.equals(changed) ? DraftOrderState.RECONCILED_CREATED
                : lateAnswer(id, DraftOrderState.CREATED, erpOrderRef, null, now);
    }

    @Override
    public Optional<DraftOrderState> stateOf(String quoteRef, int quoteVersion) {
        if (quoteRef == null || quoteVersion < 1) {
            throw new IllegalArgumentException("invalid quote reference or version");
        }
        // EP-09c round-2 review (backend M1): the background sweeper covers every request; a read expires only
        // the request it reads, so a customer request runs no other customer's listeners.
        Optional<DraftOrderStore.Request> request = store.find(quoteRef, quoteVersion);
        if (request.isEmpty()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        if (expire(request.get().id(), now.minus(properties.submitTimeout()), now)) {
            return store.find(quoteRef, quoteVersion).map(DraftOrderStore.Request::state);
        }
        return request.map(DraftOrderStore.Request::state);
    }

    /**
     * Unfinished requests past the submit timeout become OUTCOME_UNKNOWN. Each request has its own
     * transaction, which quote and the handoff join through the event; a failure for one request is
     * logged and leaves that request for the next sweep, without touching the others or the caller.
     * Called by the background sweeper ({@link DraftOrderSweeper}, EP-09c) and, as a safety net, by the
     * read paths.
     *
     * @param pages how many keyset pages of {@link #MAX_SWEPT} requests to read at most
     * @return how many requests this sweep turned into unknown outcomes
     */
    int sweep(int pages) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(properties.submitTimeout());
        int expired = 0;
        long afterId = 0;
        for (int page = 0; page < pages; page++) {
            List<Long> ids = store.unfinishedBefore(cutoff, afterId, MAX_SWEPT);
            for (Long id : ids) {
                expired += expire(id, cutoff, now) ? 1 : 0;
                afterId = id;
            }
            if (ids.size() < MAX_SWEPT) {
                break;
            }
        }
        return expired;
    }

    private boolean expire(long id, Instant cutoff, Instant now) {
        try {
            Boolean done = ownTransaction.execute(tx -> store.expireUnfinished(id, cutoff, now).map(request -> {
                store.event(request.id(), "TIMED_OUT", null, now);
                events.publishEvent(new DraftOrderStateChanged(request.quoteRef(), request.quoteVersion(),
                        DraftOrderState.OUTCOME_UNKNOWN));
                log.warn("order.request timed_out");
                return true;
            }).orElse(false));
            return Boolean.TRUE.equals(done);
        } catch (RuntimeException failure) {
            log.error("order.request timeout_not_recorded exception={}", failure.getClass().getSimpleName());
            return false;
        }
    }

    private void expireUnfinished() {
        sweep(READ_PATH_PAGES);
    }

    @Override
    public List<UnknownDraftOrder> unknown(SessionIdentity staff) {
        Team team = SalesStaff.teamOf(staff, "reconcile", audit);
        expireUnfinished();
        List<UnknownDraftOrder> unknown = store.unknown(team.value(), MAX_LISTED).stream()
                .map(request -> new UnknownDraftOrder(request.id(), request.quoteRef(), request.quoteVersion(),
                        request.idempotencyKey(), request.completedAt(), request.lateErpOrderRef()))
                .toList();
        // AUDIT-READS (A-52): best effort, collapsed per minute.
        audit.recordSeparately(new AuditEvent(AuditEvent.Action.STAFF_LIST_READ, AuditEvent.Resource.DRAFT_ORDER,
                "UNKNOWN", staff.auditActor()));
        return unknown;
    }

    @Override
    public DraftOrderState reconcile(SessionIdentity staff, long requestId, Finding finding, String erpOrderRef) {
        Team team = SalesStaff.teamOf(staff, "reconcile", audit);
        Objects.requireNonNull(finding, "finding");
        boolean created = finding == Finding.CREATED;
        if (created != (erpOrderRef != null)
                || (erpOrderRef != null && !DraftOrderOutcome.ERP_ORDER_REF.matcher(erpOrderRef).matches())) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        DraftOrderState target = created ? DraftOrderState.RECONCILED_CREATED : DraftOrderState.RECONCILED_ABSENT;
        Instant now = clock.instant();
        String actor = staff.principal().pseudonym();
        AuditActor auditActor = staff.auditActor();
        try {
            return reconcile(requestId, team, target, erpOrderRef, actor, auditActor, now);
        } catch (DeniedInTransaction denied) {
            // Recorded after the transaction returned its connection (EP-09b reviews M-1).
            audit.recordSeparately(denied.event());
            throw new ApplicationException(ErrorCode.NOT_FOUND);
        } catch (DraftOrderMirrorRefused quoteCannotFollow) {
            // Rolled back: the request is still unknown, and the mismatch needs a look before a decision.
            log.error("order.reconcile refused code=CONFLICT reason=MIRROR_REFUSED");
            throw new ApplicationException(ErrorCode.CONFLICT);
        } catch (PessimisticLockingFailureException busy) {
            // A listener (quote, handoff) waited too long for a lock: rolled back, safe to repeat.
            log.warn("order.reconcile refused code=CONFLICT reason=LOCK_BUSY");
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }

    private DraftOrderState reconcile(long requestId, Team team, DraftOrderState target, String erpOrderRef,
            String actor, AuditActor auditActor, Instant now) {
        return transactions.execute(tx -> {
            // Another team's request and a missing one are the same 404.
            DraftOrderStore.Request request = store.findForTeam(requestId, team.value())
                    .orElseThrow(() -> {
                        log.warn("order.reconcile denied code=FORBIDDEN_RESOURCE");
                        return new DeniedInTransaction(AuditEvent.denied(AuditEvent.Resource.DRAFT_ORDER,
                                AuditEvent.FORBIDDEN_RESOURCE, auditActor));
                    });
            if (request.state() == target && Objects.equals(request.erpOrderRef(), erpOrderRef)) {
                return target; // the same decision again
            }
            // A decision must not contradict what the ERP itself reported after the timeout.
            boolean contradictsErp = request.lateErpOrderRef() != null
                    && !request.lateErpOrderRef().equals(erpOrderRef);
            if (request.state() != DraftOrderState.OUTCOME_UNKNOWN || contradictsErp
                    || !store.transition(requestId, DraftOrderState.OUTCOME_UNKNOWN, target, erpOrderRef, null, now)) {
                log.warn("order.reconcile refused code=CONFLICT");
                throw new ApplicationException(ErrorCode.CONFLICT);
            }
            store.event(requestId, target.name(), actor, now);
            // EP-09b (A-41(o)): committed with the reconciliation (decision 2A).
            audit.record(new AuditEvent(AuditEvent.Action.ORDER_RECONCILED, AuditEvent.Resource.DRAFT_ORDER,
                    target.name(), auditActor));
            events.publishEvent(new DraftOrderStateChanged(request.quoteRef(), request.quoteVersion(), target));
            log.info("order.reconcile state={}", target);
            return target;
        });
    }
}
