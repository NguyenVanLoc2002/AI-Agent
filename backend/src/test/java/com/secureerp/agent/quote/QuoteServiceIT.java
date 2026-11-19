package com.secureerp.agent.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.TestcontainersConfiguration;
import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.gateway.EgressScan;
import com.secureerp.agent.gateway.api.ProductSearchResult;
import com.secureerp.agent.gateway.api.QuoteRequestResult;
import com.secureerp.agent.gateway.api.ToolErrorResult;
import com.secureerp.agent.gateway.api.ToolProposal;
import com.secureerp.agent.identity.AuthenticatedCustomerRef;
import com.secureerp.agent.identity.IdentityFixtures;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.tools.ToolCallContext;
import com.secureerp.agent.tools.ToolFixtures;
import com.secureerp.agent.tools.ToolManager;
import com.secureerp.agent.tools.ToolOutcome;
import com.secureerp.agent.tools.spi.ConversationAccessPort;
import com.secureerp.agent.tools.spi.ConversationAccessPort.Access;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * EP-06 against PostgreSQL (Flyway V6) and the Mock ERP: requestQuote end to end, quote card
 * rendering and handle binding. T-SEC01-QUOTE, T-TOOL-09, T-TOOL-10, T-UC03-RENDER, -MONEY,
 * -INELIGIBLE, -TOKEN-REPLAY, one ISSUED quote per conversation under concurrency.
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@Import({TestcontainersConfiguration.class, QuoteServiceIT.ConversationAccess.class})
class QuoteServiceIT {

    /** Mock ERP sentinels: customer prices, cost, credit balance (never in a model payload or log). */
    private static final List<String> RESTRICTED = List.of("555444333", "666777888", "987654321", "123456789");

    @TestConfiguration
    static class ConversationAccess {

        @Bean
        ConversationAccessPort conversationAccess() {
            return ToolFixtures.accessPort((principal, conversation) -> Access.ACTIVE, BOUND::get);
        }
    }

    /** A-35: the customer each test conversation is bound to; filled by {@link #context}. */
    private static final Map<ConversationId, AuthenticatedCustomerRef> BOUND = new ConcurrentHashMap<>();

    @Autowired
    ToolManager tools;

    @Autowired
    QuoteService quotes;

    @Autowired
    QuoteCards cards;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from tool_invocation");
    }

    private static ToolCallContext context(int conversation, long turn, String customer) {
        ToolCallContext base = ToolFixtures.customer("quoter" + conversation, conversation, turn);
        AuthenticatedCustomerRef ref = customer == null ? null : IdentityFixtures.customer(customer);
        if (ref == null) {
            BOUND.remove(base.conversation());
        } else {
            BOUND.put(base.conversation(), ref);
        }
        return ToolFixtures.withCustomer(base, ref);
    }

    /** A VERIFIED productRef for the OE, issued to this conversation by a real search. */
    private String verifiedRef(ToolCallContext context, String oe) {
        ProductSearchResult result = (ProductSearchResult) tools.execute(context,
                new ToolProposal("searchVerifiedProduct", "{\"oe\":\"" + oe + "\"}")).result();
        return result.candidates().getFirst().productRef();
    }

    private static ToolProposal quote(String productRef, int quantity) {
        return new ToolProposal("requestQuote",
                "{\"lines\":[{\"productRef\":\"" + productRef + "\",\"quantity\":" + quantity + "}]}");
    }

    private ToolOutcome requestQuote(int conversation, String customer, String oe, int quantity) {
        String ref = verifiedRef(context(conversation, 1, customer), oe);
        return tools.execute(context(conversation, 2, customer), quote(ref, quantity));
    }

    /** T-SEC01-QUOTE + T-UC03-RENDER + T-UC03-MONEY: the model gets a handle, the card gets the prices. */
    @Test
    void issuedQuoteGivesTheModelAHandleAndTheCustomerACard(CapturedOutput output) {
        ToolOutcome outcome = requestQuote(41, ToolFixtures.CUSTOMER_A, "04465-0K290", 2);

        QuoteRequestResult result = (QuoteRequestResult) outcome.result();
        assertThat(result.status()).isEqualTo(QuoteRequestResult.Status.ISSUED);
        assertThat(result.lineCount()).isEqualTo(1);
        assertThat(outcome.handoffRequired()).isFalse();
        assertThat(result.toString()).doesNotContain(RESTRICTED.toArray(String[]::new));
        assertThat(EgressScan.containsSentinel(result.toString())).isFalse();

        QuoteCard card = cards.render(result.quoteHandle(), ToolFixtures.conversation(41),
                IdentityFixtures.customer(ToolFixtures.CUSTOMER_A)).orElseThrow();
        assertThat(card.lines()).singleElement().satisfies(line -> {
            assertThat(line.sku()).isEqualTo(new Sku("SKU-BRK-PAD-A"));
            assertThat(line.quantity()).isEqualTo(2);
            assertThat(line.unitPrice().amount()).isEqualByComparingTo("555444333");
            assertThat(line.lineTotal().amount()).isEqualByComparingTo("1110888666");
        });
        assertThat(card.total().amount()).isEqualByComparingTo("1110888666");
        assertThat(card.total().currency().getCurrencyCode()).isEqualTo("VND");
        assertThat(card.quoteRef()).matches("^BG-[A-Z2-9]{8}$");
        assertThat(card.notes()).containsExactly(QuoteCard.NOTE_VAT, QuoteCard.NOTE_FITMENT);
        // Customer B's price never appears in customer A's quote; nothing restricted is logged.
        assertThat(card.lines().getFirst().unitPrice().amount()).isNotEqualByComparingTo("666777888");
        assertThat(output.getAll()).doesNotContain(RESTRICTED.toArray(String[]::new)).doesNotContain(result.quoteHandle());
    }

    /** ADR-014: VND scale 0, HALF_UP per line (unit price 555444333.5). */
    @Test
    void halfUnitPriceIsRoundedUpPerLine() {
        QuoteRequestResult result = (QuoteRequestResult) requestQuote(42, ToolFixtures.CUSTOMER_A, "04465-0D150", 1)
                .result();

        QuoteCard card = cards.render(result.quoteHandle(), ToolFixtures.conversation(42),
                IdentityFixtures.customer(ToolFixtures.CUSTOMER_A)).orElseThrow();
        assertThat(card.lines().getFirst().lineTotal().amount()).isEqualByComparingTo("555444334");
        assertThat(card.total().amount()).isEqualByComparingTo("555444334");
    }

    /**
     * T-TOOL-09 / T-UC03-INELIGIBLE: credit blocked (B), credit unknown (C), out of stock and no
     * customer all give the model exactly the same result, with a handoff; the reason is stored for staff.
     */
    @Test
    void creditStockAndMissingCustomerLookIdenticalToTheModel() {
        List<ToolOutcome> outcomes = List.of(
                requestQuote(43, ToolFixtures.CUSTOMER_B, "04465-0K290", 1),
                requestQuote(44, "SynthCustomerC", "04465-0K290", 1),
                requestQuote(45, ToolFixtures.CUSTOMER_A, "90915-YZZD4", 1),
                requestQuote(46, ToolFixtures.CUSTOMER_A, "04465-0D150", 3),
                requestQuote(47, null, "04465-0K290", 1));

        assertThat(outcomes).extracting(ToolOutcome::result)
                .containsOnly(new QuoteRequestResult(QuoteRequestResult.Status.NOT_QUOTED, null, 1));
        assertThat(outcomes).allMatch(ToolOutcome::handoffRequired);
        assertThat(reasons(43, 44, 45, 46, 47)).containsExactly("CREDIT_NOT_ELIGIBLE", "CREDIT_REVIEW_REQUIRED",
                "INSUFFICIENT_STOCK", "INSUFFICIENT_STOCK", "NO_CUSTOMER");
    }

    private List<String> reasons(int... conversations) {
        List<String> reasons = new ArrayList<>();
        for (int conversation : conversations) {
            reasons.add(jdbc.queryForObject("""
                    select reason_code from quote_request where conversation_id = ? order by id desc limit 1
                    """, String.class, ToolFixtures.conversation(conversation).value()));
        }
        return reasons;
    }

    /**
     * T-TOOL-10: probing stock with different quantities only ever shows ISSUED or NOT_QUOTED (no
     * number), and the first NOT_QUOTED hands the conversation to staff, which ends the probing (EP-08
     * locks the bot); the per-conversation tool quota bounds it before that.
     */
    @Test
    void quantityProbingRevealsNoStockNumber() {
        List<QuoteRequestResult> results = new ArrayList<>();
        List<Boolean> handoffs = new ArrayList<>();
        int turn = 10;
        for (int quantity : new int[] {1, 2, 3}) {
            ToolCallContext search = context(48, turn++, ToolFixtures.CUSTOMER_A);
            String ref = verifiedRef(search, "04465-0D150");
            ToolOutcome outcome = tools.execute(context(48, turn++, ToolFixtures.CUSTOMER_A), quote(ref, quantity));
            results.add((QuoteRequestResult) outcome.result());
            handoffs.add(outcome.handoffRequired());
        }

        // A-34: after the NOT_QUOTED the conversation is handed off; a smaller quantity is not quoted
        // either, and the ERP is not asked (reason HANDED_OFF), so the probing ends at one NOT_QUOTED.
        String ref = verifiedRef(context(48, turn++, ToolFixtures.CUSTOMER_A), "04465-0D150");
        ToolOutcome afterHandoff = tools.execute(context(48, turn, ToolFixtures.CUSTOMER_A), quote(ref, 1));
        results.add((QuoteRequestResult) afterHandoff.result());
        handoffs.add(afterHandoff.handoffRequired());

        assertThat(results).extracting(QuoteRequestResult::status).containsExactly(QuoteRequestResult.Status.ISSUED,
                QuoteRequestResult.Status.ISSUED, QuoteRequestResult.Status.NOT_QUOTED,
                QuoteRequestResult.Status.NOT_QUOTED);
        assertThat(results).extracting(QuoteRequestResult::lineCount).containsOnly(1);
        assertThat(handoffs).containsExactly(false, false, true, true);
        assertThat(reasons(48)).containsExactly("HANDED_OFF");
    }

    /** A-33: a NOT_QUOTED request retires the conversation's older ISSUED quote; its card stops rendering. */
    @Test
    void aNotQuotedRequestRetiresTheOlderQuote() {
        QuoteRequestResult first = (QuoteRequestResult) requestQuote(62, ToolFixtures.CUSTOMER_A, "04465-0K290", 1)
                .result();
        ConversationId conversation = ToolFixtures.conversation(62);
        AuthenticatedCustomerRef customer = IdentityFixtures.customer(ToolFixtures.CUSTOMER_A);
        assertThat(cards.render(first.quoteHandle(), conversation, customer)).isPresent();

        requestQuote(62, ToolFixtures.CUSTOMER_A, "04465-0K290", 13);

        assertThat(cards.render(first.quoteHandle(), conversation, customer)).isEmpty();
        assertThat(jdbc.queryForList("select status from quote where conversation_id = ?", String.class,
                conversation.value())).containsExactly("SUPERSEDED");
    }

    /** A-34: at most 5 ISSUED quotes per conversation per hour; the 6th is NOT_QUOTED without an ERP call. */
    @Test
    void theSixthQuoteInAnHourIsRefused() {
        ConversationId conversation = ToolFixtures.conversation(63);
        AuthenticatedCustomerRef customer = IdentityFixtures.customer(ToolFixtures.CUSTOMER_A);
        List<QuoteOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            outcomes.add(quotes.request(new QuoteRequest(conversation, customer,
                    List.of(new QuoteRequest.Line(new Sku("SKU-AIR-FLT-A"), 1)))));
        }

        assertThat(outcomes.subList(0, 5)).allMatch(outcome -> outcome instanceof QuoteOutcome.Issued);
        assertThat(outcomes.get(5)).isEqualTo(new QuoteOutcome.NotQuoted(1));
        assertThat(reasons(63)).containsExactly("REISSUE_LIMIT");
    }

    /** A-35 / SEC-02: a session customer other than the conversation's bound customer is refused. */
    @Test
    void aSessionCustomerOtherThanTheBoundOneGetsNoQuote() {
        String ref = verifiedRef(context(64, 1, ToolFixtures.CUSTOMER_A), "04465-0K290");
        ToolCallContext otherCustomer = ToolFixtures.withCustomer(ToolFixtures.customer("quoter64", 64, 2),
                IdentityFixtures.customer(ToolFixtures.CUSTOMER_B));

        ToolOutcome outcome = tools.execute(otherCustomer, quote(ref, 1));

        assertThat(outcome.result()).isEqualTo(new ToolErrorResult(ToolErrorResult.Code.FORBIDDEN_RESOURCE));
        assertThat(outcome.handoffRequired()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from quote_request where conversation_id = ?", Integer.class,
                ToolFixtures.conversation(64).value())).isZero();
    }

    /** ADR-007: a new quote supersedes the old one, whose handle stops rendering. */
    @Test
    void aNewQuoteSupersedesTheOldOne() {
        QuoteRequestResult first = (QuoteRequestResult) requestQuote(49, ToolFixtures.CUSTOMER_A, "04465-0K290", 1)
                .result();
        QuoteRequestResult second = (QuoteRequestResult) requestQuote(49, ToolFixtures.CUSTOMER_A, "04465-0K290", 3)
                .result();
        ConversationId conversation = ToolFixtures.conversation(49);
        AuthenticatedCustomerRef customer = IdentityFixtures.customer(ToolFixtures.CUSTOMER_A);

        assertThat(cards.render(first.quoteHandle(), conversation, customer)).isEmpty();
        assertThat(cards.render(second.quoteHandle(), conversation, customer)).hasValueSatisfying(
                card -> assertThat(card.lines().getFirst().quantity()).isEqualTo(3));
        assertThat(jdbc.queryForList("select status from quote where conversation_id = ? order by id", String.class,
                conversation.value())).containsExactly("SUPERSEDED", "ISSUED");
        assertThat(jdbc.queryForObject("select count(*) from quote_handle h join quote q on q.id = h.quote_id "
                + "where q.conversation_id = ? and h.revoked_at is not null", Integer.class, conversation.value()))
                .isEqualTo(1);
    }

    /** T-UC03-TOKEN-REPLAY: any mismatch gives the same empty result. */
    @Test
    void handleOnlyRendersForItsConversationCustomerAndValidity() {
        QuoteRequestResult result = (QuoteRequestResult) requestQuote(50, ToolFixtures.CUSTOMER_A, "04465-0K290", 1)
                .result();
        String handle = result.quoteHandle();
        ConversationId own = ToolFixtures.conversation(50);
        AuthenticatedCustomerRef customer = IdentityFixtures.customer(ToolFixtures.CUSTOMER_A);
        String tampered = handle.substring(0, handle.length() - 1) + (handle.endsWith("A") ? "B" : "A");

        assertThat(cards.render(handle, own, customer)).isPresent();
        assertThat(cards.render(handle, ToolFixtures.conversation(51), customer)).isEmpty();
        assertThat(cards.render(handle, own, IdentityFixtures.customer(ToolFixtures.CUSTOMER_B))).isEmpty();
        assertThat(cards.render(tampered, own, customer)).isEmpty();
        assertThat(cards.render("qh_" + "A".repeat(22), own, customer)).isEmpty();
        assertThat(cards.render("not a handle", own, customer)).isEmpty();
        assertThat(cards.render(handle, own, null)).isEmpty();

        Instant past = Instant.now().minusSeconds(3600);
        jdbc.update("update quote set issued_at = ?, valid_until = ? where conversation_id = ?",
                Timestamp.from(past.minusSeconds(60)), Timestamp.from(past), own.value());
        assertThat(cards.render(handle, own, customer)).as("expired").isEmpty();
    }

    @Test
    void revokedOrStaleVersionHandlesDoNotRender() {
        QuoteRequestResult result = (QuoteRequestResult) requestQuote(52, ToolFixtures.CUSTOMER_A, "04465-0K290", 1)
                .result();
        ConversationId own = ToolFixtures.conversation(52);
        AuthenticatedCustomerRef customer = IdentityFixtures.customer(ToolFixtures.CUSTOMER_A);

        jdbc.update("update quote set version = 2 where conversation_id = ?", own.value());
        assertThat(cards.render(result.quoteHandle(), own, customer)).as("stale version").isEmpty();
        jdbc.update("update quote set version = 1 where conversation_id = ?", own.value());
        jdbc.update("update quote_handle set revoked_at = now() where conversation_id = ?", own.value());
        assertThat(cards.render(result.quoteHandle(), own, customer)).as("revoked").isEmpty();
    }

    /** Rule 60: concurrent requests in one conversation leave exactly one ISSUED quote. */
    @Test
    void concurrentRequestsLeaveOneIssuedQuote() throws Exception {
        ConversationId conversation = ToolFixtures.conversation(53);
        AuthenticatedCustomerRef customer = IdentityFixtures.customer(ToolFixtures.CUSTOMER_A);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<QuoteOutcome>> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            calls.add(() -> {
                start.await();
                return quotes.request(new QuoteRequest(conversation, customer,
                        List.of(new QuoteRequest.Line(new Sku("SKU-AIR-FLT-A"), 1))));
            });
        }
        List<QuoteOutcome> outcomes = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            List<Future<QuoteOutcome>> futures = calls.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<QuoteOutcome> future : futures) {
                outcomes.add(future.get());
            }
        }

        // At most one ISSUED quote, and if one is left it is the newest snapshot (backend review M-1): a
        // request that started before the current quote was issued is NOT_QUOTED (STALE_REQUEST). A-33/A-34
        // may leave none (a refused request retires the older quote), never two.
        Integer issuedNow = jdbc.queryForObject(
                "select count(*) from quote where conversation_id = ? and status = 'ISSUED'", Integer.class,
                conversation.value());
        assertThat(issuedNow).isBetween(0, 1);
        assertThat(jdbc.queryForObject("""
                select count(*) from quote q where q.conversation_id = ? and q.status = 'ISSUED'
                  and q.issued_at = (select max(issued_at) from quote where conversation_id = q.conversation_id)
                """, Integer.class, conversation.value())).isEqualTo(issuedNow);
        long issued = outcomes.stream().filter(outcome -> outcome instanceof QuoteOutcome.Issued).count();
        assertThat(issued).isBetween(1L, 5L);
        assertThat(jdbc.queryForObject("select count(*) from quote_request where conversation_id = ?", Integer.class,
                conversation.value())).isEqualTo(10);
        long rendering = outcomes.stream()
                .filter(outcome -> outcome instanceof QuoteOutcome.Issued issuedOutcome
                        && cards.render(issuedOutcome.quoteHandle(), conversation, customer).isPresent())
                .count();
        assertThat(rendering).isEqualTo(issuedNow.longValue());
    }

    /** A lost race (STALE_REQUEST) does not lock the conversation: the next request is quoted. */
    @Test
    void aStaleRequestDoesNotLockTheConversation() {
        ConversationId conversation = ToolFixtures.conversation(65);
        jdbc.update("""
                insert into quote_request (conversation_id, outcome, reason_code, line_count, occurred_at)
                values (?, 'NOT_QUOTED', 'STALE_REQUEST', 1, now())
                """, conversation.value());

        assertThat(quotes.request(new QuoteRequest(conversation, IdentityFixtures.customer(ToolFixtures.CUSTOMER_A),
                List.of(new QuoteRequest.Line(new Sku("SKU-AIR-FLT-A"), 1))))).isInstanceOf(QuoteOutcome.Issued.class);
    }

    /** ADR-007: an ISSUED quote past its validity is retired as EXPIRED (not SUPERSEDED) by the next one. */
    @Test
    void anExpiredQuoteIsRetiredAsExpired() {
        requestQuote(55, ToolFixtures.CUSTOMER_A, "04465-0K290", 1);
        ConversationId conversation = ToolFixtures.conversation(55);
        Instant past = Instant.now().minusSeconds(3600);
        jdbc.update("update quote set issued_at = ?, valid_until = ? where conversation_id = ?",
                Timestamp.from(past.minusSeconds(60)), Timestamp.from(past), conversation.value());

        requestQuote(55, ToolFixtures.CUSTOMER_A, "04465-0K290", 2);

        assertThat(jdbc.queryForList("select status from quote where conversation_id = ? order by id", String.class,
                conversation.value())).containsExactly("EXPIRED", "ISSUED");
    }

    /** Backend review L4: card amounts come back at the quote's money scale (VND: 0), not NUMERIC(19,4). */
    @Test
    void cardAmountsUseTheQuoteMoneyScale() {
        QuoteRequestResult result = (QuoteRequestResult) requestQuote(56, ToolFixtures.CUSTOMER_A, "04465-0D150", 1)
                .result();

        QuoteCard card = cards.render(result.quoteHandle(), ToolFixtures.conversation(56),
                IdentityFixtures.customer(ToolFixtures.CUSTOMER_A)).orElseThrow();
        assertThat(card.total().amount().scale()).isZero();
        assertThat(card.lines().getFirst().lineTotal().amount().scale()).isZero();
        assertThat(card.lines().getFirst().unitPrice().amount()).isEqualTo(new BigDecimal("555444333.5"));
    }

    /** Security review L-4: a handle row whose conversation differs from its quote's is foreign. */
    @Test
    void aHandleBoundToAnotherConversationThanItsQuoteDoesNotRender() {
        QuoteRequestResult result = (QuoteRequestResult) requestQuote(57, ToolFixtures.CUSTOMER_A, "04465-0K290", 1)
                .result();
        jdbc.update("update quote_handle set conversation_id = ? where conversation_id = ?",
                ToolFixtures.conversation(58).value(), ToolFixtures.conversation(57).value());

        assertThat(cards.render(result.quoteHandle(), ToolFixtures.conversation(58),
                IdentityFixtures.customer(ToolFixtures.CUSTOMER_A))).isEmpty();
    }

    /** The database refuses a quote line outside BR-10 (defence below the service). */
    @Test
    void databaseConstraintsBackTheLimits() {
        requestQuote(54, ToolFixtures.CUSTOMER_A, "04465-0K290", 1);
        Long quoteId = jdbc.queryForObject("select id from quote where conversation_id = ?", Long.class,
                ToolFixtures.conversation(54).value());

        for (String bad : new String[] {"0", "10000"}) {
            assertThatThrownBy(() -> jdbc.update("""
                    insert into quote_line (quote_id, line_no, sku, quantity, unit_price, line_total, price_source,
                                            price_revision)
                    values (?, 2, 'SKU-X', ?, 1, 1, 'x', 'r1')
                    """, quoteId, Integer.parseInt(bad))).isInstanceOf(
                    DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("""
                insert into quote_line (quote_id, line_no, sku, quantity, unit_price, line_total, price_source,
                                        price_revision)
                values (?, 21, 'SKU-Y', 1, ?, 1, 'x', 'r1')
                """, quoteId, new BigDecimal("1"))).isInstanceOf(
                DataIntegrityViolationException.class);
    }
}
