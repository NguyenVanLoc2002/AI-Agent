package com.secureerp.agent.quote.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.TestcontainersConfiguration;
import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.handoff.HandoffInbox;
import com.secureerp.agent.handoff.HandoffReason;
import com.secureerp.agent.handoff.Handoffs;
import com.secureerp.agent.identity.IdentityFixtures;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.platform.Money;
import com.secureerp.agent.quote.spi.PriceQuote;
import com.secureerp.agent.tools.ToolFixtures;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A-43(s) (owner 2026-11-23, 1A–4A) against PostgreSQL: a business refusal that locks the customer (A-34)
 * opens its handoff in the transaction that stores it, so a staff resolve can never lift a refusal staff
 * have not seen. Two transactions race through the handoff lock; the refusal is stored alone when its
 * handoff cannot be opened (a real handoff lock timeout).
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@Import(TestcontainersConfiguration.class)
class RefusalHandoffIT {

    @Autowired
    QuoteStore store;

    @Autowired
    Handoffs handoffs;

    @Autowired
    HandoffInbox inbox;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transactions;

    /** {@code JdbcHandoffs.LOCK_NAMESPACE}: the conversation's handoff lock (package-private there). */
    private static final int HANDOFF_LOCK = 30_012;
    private static final Currency VND = Currency.getInstance("VND");

    private final QuoteTokens tokens = new QuoteTokens();
    private final SessionIdentity sales = IdentityFixtures.salesIdentity("refusalT1", IdentityFixtures.TEAM_1);

    @BeforeEach
    void clean() {
        for (String table : List.of("handoff", "quote_lock_lift", "quote_request", "quote_handle", "quote_line",
                "quote", "message", "conversation")) {
            jdbc.update("delete from " + table);
        }
    }

    /** A fresh customer per test, so the per-customer A-34 lock of one test never reaches another. */
    private static String customer() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }

    private ConversationId conversation(int seed, String customer, String status) {
        ConversationId id = ToolFixtures.conversation(seed);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("insert into conversation (conversation_id, owner_principal, customer_digest, channel, status, "
                        + "created_at, updated_at, team) values (?, ?, ?, 'WEB_CHAT', ?, ?, ?, 'team-1')", id.value(),
                IdentityFixtures.principal("refusalOwner").pseudonym(), customer, status, now, now);
        return id;
    }

    private static QuoteStore.Limits limits() {
        Instant now = Instant.now();
        return new QuoteStore.Limits(now.minus(Duration.ofHours(24)), now.minus(Duration.ofHours(1)), 5);
    }

    private void refuse(ConversationId conversation, String customer, NotQuotedReason reason) {
        store.recordNotQuoted(conversation, customer, reason, 1, Instant.now());
    }

    private String status(ConversationId conversation) {
        return jdbc.queryForObject("select status from conversation where conversation_id = ?", String.class,
                conversation.value());
    }

    private List<String> handoffRows(ConversationId conversation) {
        return jdbc.queryForList("select reason_code || ':' || status || ':' || coalesce(followup_reason, '-') "
                + "from handoff where conversation_id = ? order by handoff_id", String.class, conversation.value());
    }

    private long activeHandoff(ConversationId conversation) {
        return jdbc.queryForObject("select handoff_id from handoff where conversation_id = ? "
                + "and status in ('OPEN', 'CLAIMED')", Long.class, conversation.value());
    }

    private int lifts(ConversationId conversation) {
        return jdbc.queryForObject("select count(*) from quote_lock_lift where conversation_id = ?", Integer.class,
                conversation.value());
    }

    private int refusals(ConversationId conversation) {
        return jdbc.queryForObject("select count(*) from quote_request where conversation_id = ? "
                + "and outcome = 'NOT_QUOTED'", Integer.class, conversation.value());
    }

    /** The refusal and its handoff commit together; the bot is locked at once, not at the end of the turn. */
    @Test
    void aRefusalThatLocksTheCustomerOpensItsHandoffInTheSameTransaction(CapturedOutput output) {
        String customer = customer();
        ConversationId conversation = conversation(901, customer, "ACTIVE_BOT");

        refuse(conversation, customer, NotQuotedReason.INSUFFICIENT_STOCK);

        assertThat(handoffRows(conversation)).containsExactly("QUOTE_NOT_ISSUED:OPEN:-");
        assertThat(status(conversation)).isEqualTo("HANDOFF_PENDING");
        assertThat(store.gate(customer, limits())).contains(NotQuotedReason.HANDED_OFF);
        // Security review L-3: logged as a refusal, not as a confirmation; never the internal reason.
        assertThat(output.getAll()).contains("conversation.handoff quote trigger=NOT_QUOTED reason=QUOTE_NOT_ISSUED")
                .doesNotContain("quote_confirmation").doesNotContain("INSUFFICIENT_STOCK").doesNotContain(customer);
        // The end of the chat turn opens it again with the same reason: no second handoff, no follow-up (3A).
        handoffs.open(conversation, HandoffReason.QUOTE_NOT_ISSUED, IdentityFixtures.TEAM_1);
        assertThat(handoffRows(conversation)).containsExactly("QUOTE_NOT_ISSUED:OPEN:-");
    }

    /**
     * The gap of A-43(s): the refusal is committed while staff hold another handoff of the conversation and
     * resolve it before the chat turn ends. The refusal is a follow-up, so the resolve reopens instead of
     * lifting the lock; the customer stays locked everywhere until staff resolve the refusal itself.
     */
    @Test
    void aRefusalWhileStaffHoldTheConversationIsAFollowUpAndTheResolveKeepsTheLock() {
        String customer = customer();
        ConversationId conversation = conversation(902, customer, "ACTIVE_BOT");
        handoffs.open(conversation, HandoffReason.PROVIDER_ERROR, IdentityFixtures.TEAM_1);
        jdbc.update("update conversation set status = 'HANDOFF_PENDING' where conversation_id = ?",
                conversation.value());
        long held = activeHandoff(conversation);
        inbox.claim(sales, held);

        refuse(conversation, customer, NotQuotedReason.CREDIT_NOT_ELIGIBLE);

        assertThat(handoffRows(conversation)).containsExactly("PROVIDER_ERROR:CLAIMED:QUOTE_NOT_ISSUED");
        assertThat(status(conversation)).isEqualTo("HUMAN_ACTIVE");

        inbox.resolve(sales, held);

        assertThat(lifts(conversation)).as("no lift before staff saw the refusal").isZero();
        assertThat(store.gate(customer, limits())).contains(NotQuotedReason.HANDED_OFF);
        assertThat(handoffRows(conversation)).containsExactly("PROVIDER_ERROR:RESOLVED:QUOTE_NOT_ISSUED",
                "QUOTE_NOT_ISSUED:OPEN:-");
        assertThat(status(conversation)).isEqualTo("HANDOFF_PENDING");

        // Resolving the refusal's own handoff lifts it (EP-08b, unchanged).
        long followUp = activeHandoff(conversation);
        inbox.claim(sales, followUp);
        inbox.resolve(sales, followUp);
        assertThat(lifts(conversation)).isEqualTo(1);
        assertThat(store.gate(customer, limits())).isEmpty();
    }

    /**
     * The other order, with two transactions: a resolve holds the handoff lock (its lift already ran) while a
     * refusal is being stored. The refusal waits for the lock, is not covered by the lift, and opens a new
     * handoff after the resolve committed: locked customer, bot locked again.
     */
    @Test
    void aRefusalRacingAResolveIsNotLiftedAndOpensANewHandoff() throws Exception {
        String customer = customer();
        ConversationId conversation = conversation(903, customer, "ACTIVE_BOT");
        refuse(conversation, customer, NotQuotedReason.INSUFFICIENT_STOCK);
        long first = activeHandoff(conversation);
        inbox.claim(sales, first);

        CountDownLatch resolvedNotCommitted = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> resolve = pool.submit(() -> transactions.executeWithoutResult(tx -> {
                inbox.resolve(sales, first);
                resolvedNotCommitted.countDown();
                await(commit);
            }));
            assertThat(resolvedNotCommitted.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> refusal = pool.submit(() -> refuse(conversation, customer, NotQuotedReason.PRICE_NOT_FOUND));
            awaitAdvisoryLockWaiter();
            commit.countDown();
            resolve.get(10, TimeUnit.SECONDS);
            refusal.get(10, TimeUnit.SECONDS);
        } finally {
            commit.countDown();
            pool.shutdownNow();
        }

        assertThat(lifts(conversation)).as("the resolve lifted the first refusal").isEqualTo(1);
        assertThat(refusals(conversation)).isEqualTo(2);
        assertThat(store.gate(customer, limits())).as("the racing refusal still locks")
                .contains(NotQuotedReason.HANDED_OFF);
        assertThat(handoffRows(conversation)).containsExactly("QUOTE_NOT_ISSUED:RESOLVED:-", "QUOTE_NOT_ISSUED:OPEN:-");
        assertThat(status(conversation)).isEqualTo("HANDOFF_PENDING");
    }

    /** Technical failures, limits and refusals while locked do not lock: their handoff stays at the end of the turn. */
    @Test
    void aRefusalThatDoesNotLockOpensNoHandoffHere() {
        String customer = customer();
        ConversationId conversation = conversation(904, customer, "ACTIVE_BOT");

        for (NotQuotedReason reason : NotQuotedReason.values()) {
            if (!reason.locksAndRetires()) {
                refuse(conversation, customer, reason);
            }
        }
        refuse(conversation, null, NotQuotedReason.NO_CUSTOMER);

        assertThat(handoffRows(conversation)).isEmpty();
        assertThat(status(conversation)).isEqualTo("ACTIVE_BOT");
        assertThat(store.gate(customer, limits())).isEmpty();
    }

    /** A-35: no handoff on a conversation bound to another customer; the refusal still locks its customer. */
    @Test
    void aRefusalOnAConversationOfAnotherCustomerOpensNoHandoffButStillLocks(CapturedOutput output) {
        String customer = customer();
        ConversationId conversation = conversation(905, customer(), "ACTIVE_BOT");

        refuse(conversation, customer, NotQuotedReason.INSUFFICIENT_STOCK);

        assertThat(handoffRows(conversation)).isEmpty();
        assertThat(status(conversation)).isEqualTo("ACTIVE_BOT");
        assertThat(store.gate(customer, limits())).contains(NotQuotedReason.HANDED_OFF);
        assertThat(output.getAll()).contains("code=CUSTOMER_NOT_BOUND");
    }

    /**
     * 2A, with the failure named in the decision: staff hold the handoff lock past the 5 s lock timeout. The
     * refusal is not lost with its handoff: it is stored alone (once), the older quote is retired once, the
     * customer stays locked; the chat turn still hands off at its end. The log names the failure class only
     * (backend review M-2, security review L-2).
     */
    @Test
    void aRefusalWhoseHandoffLockTimesOutIsStoredAloneAndStillLocks(CapturedOutput output) throws Exception {
        String customer = customer();
        ConversationId conversation = conversation(906, customer, "ACTIVE_BOT");
        assertThat(store.issue(quote(conversation, customer, Instant.now().minusSeconds(1)), limits()))
                .isEqualTo(QuoteStore.IssueResult.ISSUED);

        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        long elapsedMs;
        try {
            Future<?> staff = pool.submit(() -> transactions.executeWithoutResult(tx -> {
                jdbc.queryForList("select pg_advisory_xact_lock(?, hashtext(?))", HANDOFF_LOCK, conversation.value());
                held.countDown();
                await(release);
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            long started = System.nanoTime();
            refuse(conversation, customer, NotQuotedReason.INSUFFICIENT_STOCK);
            elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            release.countDown();
            staff.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }

        assertThat(elapsedMs).as("one lock timeout, then the refusal alone").isBetween(4_500L, 12_000L);
        assertThat(refusals(conversation)).isEqualTo(1);
        assertThat(jdbc.queryForList("select status from quote where conversation_id = ?", String.class,
                conversation.value())).containsExactly("SUPERSEDED");
        assertThat(jdbc.queryForObject("select count(*) from quote_handle where conversation_id = ? "
                + "and revoked_at is null", Integer.class, conversation.value())).isZero();
        assertThat(store.gate(customer, limits())).contains(NotQuotedReason.HANDED_OFF);
        assertThat(handoffRows(conversation)).isEmpty();
        assertThat(status(conversation)).isEqualTo("ACTIVE_BOT");
        assertThat(output.getAll()).contains("quote.not_quoted handoff_failed exception=")
                .doesNotContain(customer).doesNotContain("INSUFFICIENT_STOCK");
    }

    /**
     * Intended (A-48(a), owner 2026-11-23, 1A; security review L-1): a refusal that meets an OPEN handoff
     * with the same reason (e.g. a blocked confirmation not claimed yet) joins it without a follow-up. Staff
     * claim after the refusal was committed, so resolving that handoff lifts it under the EP-08b rule (A-43
     * 7A: staff dealt with the conversation). A follow-up would carry the same reason (SALES never see the
     * internal one, A-43 5A) and only ask staff to resolve twice. Unlike A-43(s), the resolve cannot come
     * before the refusal's handoff exists.
     */
    @Test
    void aRefusalMergedIntoAnOpenHandoffIsLiftedWhenStaffResolveItAfterTheRefusal() {
        String customer = customer();
        ConversationId conversation = conversation(908, customer, "ACTIVE_BOT");
        handoffs.open(conversation, HandoffReason.QUOTE_NOT_ISSUED, IdentityFixtures.TEAM_1);
        jdbc.update("update conversation set status = 'HANDOFF_PENDING' where conversation_id = ?",
                conversation.value());

        refuse(conversation, customer, NotQuotedReason.CREDIT_NOT_ELIGIBLE);
        assertThat(handoffRows(conversation)).containsExactly("QUOTE_NOT_ISSUED:OPEN:-");

        long open = activeHandoff(conversation);
        inbox.claim(sales, open);
        inbox.resolve(sales, open);

        assertThat(lifts(conversation)).isEqualTo(1);
        assertThat(store.gate(customer, limits())).isEmpty();
    }

    private QuoteStore.NewQuote quote(ConversationId conversation, String customer, Instant issuedAt) {
        QuoteCalculator.Calculation calculation = QuoteCalculator.calculate(
                List.of(new QuoteCalculator.PricedLine(new Sku("SKU-AIR-FLT-A"), 1,
                        new PriceQuote(Money.of(new BigDecimal("1000"), VND), "refusal-it", null))),
                Money.zero(VND), 0, RoundingMode.HALF_UP);
        return new QuoteStore.NewQuote(tokens.newPublicRef(), conversation, customer, VND, 0, RoundingMode.HALF_UP,
                calculation, QuoteTokens.hash(tokens.newHandle()), issuedAt, issuedAt.plus(Duration.ofHours(24)));
    }

    /** Waits until another session waits for a handoff lock (the refusal); backend review L-1. */
    private void awaitAdvisoryLockWaiter() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("select count(*) from pg_locks where locktype = 'advisory' "
                    + "and not granted and classid = ? and objsubid = 2 and pid <> pg_backend_pid()",
                    Integer.class, HANDOFF_LOCK);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("the refusal never waited for the handoff lock");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("not released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
