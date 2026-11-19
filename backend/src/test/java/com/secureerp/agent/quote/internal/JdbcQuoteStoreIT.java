package com.secureerp.agent.quote.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.TestcontainersConfiguration;
import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.platform.Money;
import com.secureerp.agent.quote.spi.PriceQuote;
import com.secureerp.agent.tools.ToolFixtures;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Store-level ordering rules against PostgreSQL with explicit instants (EP-06 backend review 2):
 * newest snapshot wins, A-33/A-34 as refined by the owner on 2026-11-19 (only business refusals lock
 * and retire, per customer across conversations), foreign-customer guard, window boundaries.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JdbcQuoteStoreIT {

    private static final Currency VND = Currency.getInstance("VND");
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Autowired
    QuoteStore store;

    @Autowired
    JdbcTemplate jdbc;

    private final QuoteTokens tokens = new QuoteTokens();

    private static String digest(String seed) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(("store-it:" + seed + ":" + NOW).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static QuoteStore.Limits limits(Instant now) {
        return new QuoteStore.Limits(now.minus(Duration.ofHours(24)), now.minus(Duration.ofHours(1)), 5);
    }

    private QuoteStore.NewQuote quote(ConversationId conversation, String customer, Instant issuedAt) {
        QuoteCalculator.Calculation calculation = QuoteCalculator.calculate(
                List.of(new QuoteCalculator.PricedLine(new Sku("SKU-AIR-FLT-A"), 1,
                        new PriceQuote(Money.of(new BigDecimal("1000"), VND), "store-it", null))),
                Money.zero(VND), 0, RoundingMode.HALF_UP);
        return new QuoteStore.NewQuote(tokens.newPublicRef(), conversation, customer, VND, 0, RoundingMode.HALF_UP,
                calculation, QuoteTokens.hash(tokens.newHandle()), issuedAt, issuedAt.plus(Duration.ofHours(24)));
    }

    private List<String> statuses(ConversationId conversation) {
        return jdbc.queryForList("select status from quote where conversation_id = ? order by id", String.class,
                conversation.value());
    }

    @Test
    void anOlderRefusalLocksButDoesNotRetireANewerQuote() {
        ConversationId conversation = ToolFixtures.conversation(201);
        String customer = digest("201");

        assertThat(store.issue(quote(conversation, customer, NOW), limits(NOW)))
                .isEqualTo(QuoteStore.IssueResult.ISSUED);
        store.recordNotQuoted(conversation, customer, NotQuotedReason.INSUFFICIENT_STOCK, 1, NOW.minusSeconds(5));

        assertThat(statuses(conversation)).containsExactly("ISSUED");
        assertThat(store.gate(customer, limits(NOW))).contains(NotQuotedReason.HANDED_OFF);
    }

    @Test
    void aNewerRefusalRetiresTheQuoteAndAnOlderIssueIsLocked() {
        ConversationId conversation = ToolFixtures.conversation(202);
        String customer = digest("202");
        store.issue(quote(conversation, customer, NOW.minusSeconds(10)), limits(NOW));

        store.recordNotQuoted(conversation, customer, NotQuotedReason.CREDIT_NOT_ELIGIBLE, 1, NOW);

        assertThat(statuses(conversation)).containsExactly("SUPERSEDED");
        assertThat(store.issue(quote(conversation, customer, NOW.minusSeconds(5)), limits(NOW)))
                .isEqualTo(QuoteStore.IssueResult.LOCKED);
    }

    @Test
    void anOlderIssueIsStale() {
        ConversationId conversation = ToolFixtures.conversation(203);
        String customer = digest("203");
        store.issue(quote(conversation, customer, NOW), limits(NOW));

        assertThat(store.issue(quote(conversation, customer, NOW.minusSeconds(1)), limits(NOW)))
                .isEqualTo(QuoteStore.IssueResult.STALE);
        assertThat(statuses(conversation)).containsExactly("ISSUED");
    }

    /** Security review 2 L-1: another customer's ISSUED quote in the conversation is never replaced or retired. */
    @Test
    void anotherCustomersQuoteIsLeftAlone() {
        ConversationId conversation = ToolFixtures.conversation(204);
        String owner = digest("204-owner");
        String other = digest("204-other");
        store.issue(quote(conversation, owner, NOW.minusSeconds(10)), limits(NOW));

        assertThat(store.issue(quote(conversation, other, NOW), limits(NOW))).isEqualTo(QuoteStore.IssueResult.FOREIGN);
        store.recordNotQuoted(conversation, other, NotQuotedReason.INSUFFICIENT_STOCK, 1, NOW);

        assertThat(statuses(conversation)).containsExactly("ISSUED");
    }

    /** Owner 2026-11-19: technical failures, limits, refusals while locked and lost races neither lock nor retire. */
    @Test
    void nonBusinessReasonsNeitherLockNorRetire() {
        ConversationId conversation = ToolFixtures.conversation(205);
        String customer = digest("205");
        store.issue(quote(conversation, customer, NOW.minusSeconds(10)), limits(NOW));

        for (NotQuotedReason reason : List.of(NotQuotedReason.SOURCE_UNAVAILABLE, NotQuotedReason.STORE_UNAVAILABLE,
                NotQuotedReason.REISSUE_LIMIT, NotQuotedReason.HANDED_OFF, NotQuotedReason.STALE_REQUEST,
                NotQuotedReason.NO_CUSTOMER, NotQuotedReason.PRICE_CURRENCY_MISMATCH)) {
            store.recordNotQuoted(conversation, customer, reason, 1, NOW);
        }

        assertThat(statuses(conversation)).containsExactly("ISSUED");
        // Only the stale row counts toward the limit (1 ISSUED + 1 STALE < 5): no lock, no limit.
        assertThat(store.gate(customer, limits(NOW))).isEmpty();
    }

    @Test
    void theLockLastsTwentyFourHours() {
        String expired = digest("206-expired");
        String active = digest("206-active");
        store.recordNotQuoted(ToolFixtures.conversation(206), expired, NotQuotedReason.INSUFFICIENT_STOCK, 1,
                NOW.minus(Duration.ofHours(25)));
        store.recordNotQuoted(ToolFixtures.conversation(207), active, NotQuotedReason.INSUFFICIENT_STOCK, 1,
                NOW.minus(Duration.ofHours(23)));

        assertThat(store.gate(expired, limits(NOW))).isEmpty();
        assertThat(store.gate(active, limits(NOW))).contains(NotQuotedReason.HANDED_OFF);
        assertThat(store.gate(null, limits(NOW))).isEqualTo(Optional.empty());
    }

    /** Per customer across conversations; a lost race counts toward the limit (security review 2 L-3). */
    @Test
    void theIssueLimitIsPerCustomerAndCountsLostRaces() {
        String customer = digest("208");
        for (int i = 0; i < 4; i++) {
            assertThat(store.issue(quote(ToolFixtures.conversation(210 + i), customer, NOW.minusSeconds(60 - i)),
                    limits(NOW))).isEqualTo(QuoteStore.IssueResult.ISSUED);
        }
        assertThat(store.gate(customer, limits(NOW))).isEmpty();

        store.recordNotQuoted(ToolFixtures.conversation(215), customer, NotQuotedReason.STALE_REQUEST, 1, NOW);

        assertThat(store.gate(customer, limits(NOW))).contains(NotQuotedReason.REISSUE_LIMIT);
        assertThat(store.issue(quote(ToolFixtures.conversation(216), customer, NOW), limits(NOW)))
                .isEqualTo(QuoteStore.IssueResult.LIMIT);
        // Older than one hour no longer counts.
        assertThat(store.gate(customer, limits(NOW.plus(Duration.ofHours(2))))).isEmpty();
    }
}
