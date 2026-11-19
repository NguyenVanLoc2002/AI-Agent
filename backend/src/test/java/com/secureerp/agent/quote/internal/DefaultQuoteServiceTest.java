package com.secureerp.agent.quote.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.identity.AuthenticatedCustomerRef;
import com.secureerp.agent.identity.CustomerLinks;
import com.secureerp.agent.identity.IdentityFixtures;
import com.secureerp.agent.identity.spi.ErpCustomerLink;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.platform.Money;
import com.secureerp.agent.quote.QuoteOutcome;
import com.secureerp.agent.quote.QuoteRequest;
import com.secureerp.agent.quote.spi.EligibilityStatus;
import com.secureerp.agent.quote.spi.PriceQuote;
import com.secureerp.agent.quote.spi.QuoteSourceUnavailableException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * BR-11 / BR-35 with fake ports: every reason gives the same NOT_QUOTED, the reason is stored for
 * staff only, and logs carry no price, stock, credit status or reason (T-UC03-INELIGIBLE, unit level).
 * A-19, A-33, A-34 (owner 2026-11-18): the ports see only the ERP link; the A-34 gate runs before any
 * ERP call.
 */
@ExtendWith(OutputCaptureExtension.class)
class DefaultQuoteServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T08:00:00Z");
    private static final Currency VND = Currency.getInstance("VND");
    private static final ConversationId CONVERSATION = new ConversationId("cv_QuoteConvAAAAAAAAAAAAA");
    private static final AuthenticatedCustomerRef CUSTOMER = IdentityFixtures.customer("SynthCustomerA");
    private static final ErpCustomerLink ERP_CUSTOMER = new ErpCustomerLink("SYNTH-CUST-A");
    private static final Sku SKU = new Sku("SKU-BRK-PAD-A");

    private EligibilityStatus credit = EligibilityStatus.ELIGIBLE;
    private Optional<PriceQuote> price = Optional.of(price("555444333", VND));
    private OptionalInt stock = OptionalInt.of(12);
    private RuntimeException sourceFailure;
    private final List<ErpCustomerLink> portCustomers = new ArrayList<>();
    private int erpCalls;
    private final RecordingStore store = new RecordingStore();

    /** Only CUSTOMER is linked; any other reference has no ERP customer. */
    private RuntimeException linkFailure;
    private final CustomerLinks links = customer -> {
        if (linkFailure != null) {
            throw linkFailure;
        }
        return CUSTOMER.equals(customer) ? Optional.of(ERP_CUSTOMER) : Optional.empty();
    };

    private static PriceQuote price(String amount, Currency currency) {
        return new PriceQuote(Money.of(new BigDecimal(amount), currency), "mock-erp:pl:contract", "r1");
    }

    private DefaultQuoteService service() {
        return new DefaultQuoteService(
                (customer, sku) -> {
                    erp(customer);
                    return price;
                },
                sku -> {
                    erp(null);
                    return stock;
                },
                customer -> {
                    erp(customer);
                    return credit;
                },
                links, store,
                new QuoteProperties(VND, 0, RoundingMode.HALF_UP, Duration.ofHours(24), Duration.ofHours(24), 5),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void erp(ErpCustomerLink customer) {
        erpCalls++;
        if (customer != null) {
            portCustomers.add(customer);
        }
        if (sourceFailure != null) {
            throw sourceFailure;
        }
    }

    private QuoteOutcome request(AuthenticatedCustomerRef customer, int quantity) {
        return service().request(new QuoteRequest(CONVERSATION, customer, List.of(new QuoteRequest.Line(SKU, quantity))));
    }

    @Test
    void eligibleCustomerWithPriceAndStockIsIssued(CapturedOutput output) {
        QuoteOutcome outcome = request(CUSTOMER, 2);

        assertThat(outcome).isInstanceOf(QuoteOutcome.Issued.class);
        assertThat(store.issued).singleElement().satisfies(quote -> {
            assertThat(quote.calculation().total().amount()).isEqualByComparingTo("1110888666");
            assertThat(quote.customerDigest()).isEqualTo(CUSTOMER.bindingDigest());
            assertThat(quote.validUntil()).isEqualTo(NOW.plus(Duration.ofHours(24)));
            assertThat(quote.handleHash()).isEqualTo(QuoteTokens.hash(((QuoteOutcome.Issued) outcome).quoteHandle()));
        });
        // A-19: the ports get the ERP link, never the session reference.
        assertThat(portCustomers).containsOnly(ERP_CUSTOMER);
        assertThat(output.getAll()).contains("quote.request outcome=ISSUED lines=1")
                .doesNotContain("555444333", "1110888666", "SYNTH-CUST-A",
                        ((QuoteOutcome.Issued) outcome).quoteHandle());
    }

    @Test
    void everyReasonIsTheSameNotQuotedAndOnlyTheStoreKnowsWhy(CapturedOutput output) {
        List<QuoteOutcome> outcomes = new ArrayList<>();
        outcomes.add(request(null, 1));
        outcomes.add(request(IdentityFixtures.customer("UnlinkedCustomer"), 1));
        credit = EligibilityStatus.NOT_ELIGIBLE;
        outcomes.add(request(CUSTOMER, 1));
        credit = EligibilityStatus.REVIEW_REQUIRED;
        outcomes.add(request(CUSTOMER, 1));
        credit = EligibilityStatus.ELIGIBLE;
        price = Optional.empty();
        outcomes.add(request(CUSTOMER, 1));
        price = Optional.of(price("10", Currency.getInstance("USD")));
        outcomes.add(request(CUSTOMER, 1));
        price = Optional.of(price("555444333", VND));
        outcomes.add(request(CUSTOMER, 13));
        stock = OptionalInt.empty();
        outcomes.add(request(CUSTOMER, 1));
        sourceFailure = new QuoteSourceUnavailableException();
        outcomes.add(request(CUSTOMER, 1));
        sourceFailure = new IllegalStateException("ERP said creditBalance=123456789");
        outcomes.add(request(CUSTOMER, 1));

        assertThat(outcomes).containsOnly(new QuoteOutcome.NotQuoted(1));
        assertThat(store.notQuoted).containsExactly(NotQuotedReason.NO_CUSTOMER, NotQuotedReason.NO_CUSTOMER,
                NotQuotedReason.CREDIT_NOT_ELIGIBLE, NotQuotedReason.CREDIT_REVIEW_REQUIRED,
                NotQuotedReason.PRICE_NOT_FOUND, NotQuotedReason.PRICE_CURRENCY_MISMATCH,
                NotQuotedReason.INSUFFICIENT_STOCK, NotQuotedReason.INSUFFICIENT_STOCK,
                NotQuotedReason.SOURCE_UNAVAILABLE, NotQuotedReason.SOURCE_UNAVAILABLE);
        assertThat(store.issued).isEmpty();
        assertThat(output.getAll()).doesNotContain("CREDIT", "STOCK", "PRICE_", "123456789", "555444333");
    }

    /** A-34: a locked or rate-limited conversation is NOT_QUOTED before any ERP call (no stock probing). */
    @Test
    void theGateRefusesBeforeAnyErpCall() {
        store.gate = Optional.of(NotQuotedReason.HANDED_OFF);
        assertThat(request(CUSTOMER, 1)).isEqualTo(new QuoteOutcome.NotQuoted(1));
        store.gate = Optional.of(NotQuotedReason.REISSUE_LIMIT);
        assertThat(request(CUSTOMER, 9999)).isEqualTo(new QuoteOutcome.NotQuoted(1));

        assertThat(erpCalls).isZero();
        assertThat(store.notQuoted).containsExactly(NotQuotedReason.HANDED_OFF, NotQuotedReason.REISSUE_LIMIT);
        assertThat(store.lastLimits.lockSince()).isEqualTo(NOW.minus(Duration.ofHours(24)));
        assertThat(store.lastLimits.issuedSince()).isEqualTo(NOW.minus(Duration.ofHours(1)));
        assertThat(store.lastLimits.maxIssued()).isEqualTo(5);
    }

    /** A-34 re-checked under the lock, backend review M-1 (stale), foreign quote: all NOT_QUOTED with a reason. */
    @Test
    void lockedLimitedStaleOrForeignAtIssueTimeIsNotQuoted() {
        store.issueResult = QuoteStore.IssueResult.LOCKED;
        assertThat(request(CUSTOMER, 1)).isEqualTo(new QuoteOutcome.NotQuoted(1));
        store.issueResult = QuoteStore.IssueResult.LIMIT;
        assertThat(request(CUSTOMER, 1)).isEqualTo(new QuoteOutcome.NotQuoted(1));
        store.issueResult = QuoteStore.IssueResult.STALE;
        assertThat(request(CUSTOMER, 1)).isEqualTo(new QuoteOutcome.NotQuoted(1));
        store.issueResult = QuoteStore.IssueResult.FOREIGN;
        assertThat(request(CUSTOMER, 1)).isEqualTo(new QuoteOutcome.NotQuoted(1));

        assertThat(store.notQuoted).containsExactly(NotQuotedReason.HANDED_OFF, NotQuotedReason.REISSUE_LIMIT,
                NotQuotedReason.STALE_REQUEST, NotQuotedReason.CONVERSATION_MISMATCH);
    }

    /** The A-34 gate is per customer: it gets the customer's binding digest (null without a customer). */
    @Test
    void theGateIsAskedPerCustomer() {
        request(CUSTOMER, 1);
        request(null, 1);

        assertThat(store.gatedCustomers).containsExactly(CUSTOMER.bindingDigest(), null);
    }

    /** Backend review 2 L-4: a failing link lookup is our database, not an ERP outage. */
    @Test
    void aLinkLookupFailureIsAStoreFailure() {
        linkFailure = new IllegalStateException("db down");

        assertThat(request(CUSTOMER, 1)).isEqualTo(new QuoteOutcome.NotQuoted(1));
        assertThat(store.notQuoted).containsExactly(NotQuotedReason.STORE_UNAVAILABLE);
        assertThat(erpCalls).isZero();
    }

    /** A-33/A-34 (owner 2026-11-19): only business refusals lock and retire. */
    @Test
    void onlyBusinessRefusalsLockAndRetire() {
        assertThat(Arrays.stream(NotQuotedReason.values()).filter(NotQuotedReason::locksAndRetires))
                .containsExactlyInAnyOrder(NotQuotedReason.CREDIT_NOT_ELIGIBLE, NotQuotedReason.CREDIT_REVIEW_REQUIRED,
                        NotQuotedReason.PRICE_NOT_FOUND, NotQuotedReason.INSUFFICIENT_STOCK);
    }

    /**
     * EP-06 reviews (backend M-2, security M-3): failures after the ERP checks are the same NOT_QUOTED,
     * also when the audit row cannot be written, so the tool layer always hands off.
     */
    @Test
    void failuresAfterTheErpChecksAreNotQuotedToo(CapturedOutput output) {
        store.failIssue = new IllegalStateException("duplicate key public_ref; total 1110888666");
        assertThat(request(CUSTOMER, 2)).isEqualTo(new QuoteOutcome.NotQuoted(1));
        assertThat(store.notQuoted).containsExactly(NotQuotedReason.STORE_UNAVAILABLE);

        store.failAudit = new IllegalStateException("db down");
        assertThat(request(CUSTOMER, 2)).isEqualTo(new QuoteOutcome.NotQuoted(1));
        assertThat(output.getAll()).contains("quote.request audit_failed")
                .doesNotContain("1110888666", "555444333");
    }

    @Test
    void aGateFailureIsNotQuoted() {
        store.failGate = new IllegalStateException("db down");

        assertThat(request(CUSTOMER, 1)).isEqualTo(new QuoteOutcome.NotQuoted(1));
        assertThat(erpCalls).isZero();
        assertThat(store.notQuoted).containsExactly(NotQuotedReason.STORE_UNAVAILABLE);
    }

    /** A price whose total does not fit the Money range is NOT_QUOTED, not an exception. */
    @Test
    void anOverflowingTotalIsNotQuoted() {
        price = Optional.of(price("999999999999999", VND));
        stock = OptionalInt.of(9999);

        assertThat(request(CUSTOMER, 9999)).isEqualTo(new QuoteOutcome.NotQuoted(1));
        assertThat(store.notQuoted).containsExactly(NotQuotedReason.STORE_UNAVAILABLE);
    }

    @Test
    void stockExactlyEqualToTheQuantityIsEnough() {
        assertThat(request(CUSTOMER, 12)).isInstanceOf(QuoteOutcome.Issued.class);
    }

    @Test
    void handlesAreRandomAndWellFormed() {
        QuoteOutcome.Issued first = (QuoteOutcome.Issued) request(CUSTOMER, 1);
        QuoteOutcome.Issued second = (QuoteOutcome.Issued) request(CUSTOMER, 1);

        assertThat(first.quoteHandle()).matches("^qh_[A-Za-z0-9_-]{22}$").isNotEqualTo(second.quoteHandle());
        assertThat(store.issued).extracting(QuoteStore.NewQuote::publicRef).allMatch(ref -> ref.matches("^BG-[A-Z2-9]{8}$"));
    }

    private static final class RecordingStore implements QuoteStore {

        private final List<NotQuotedReason> notQuoted = new ArrayList<>();
        private final List<NewQuote> issued = new ArrayList<>();
        private Optional<NotQuotedReason> gate = Optional.empty();
        private IssueResult issueResult = IssueResult.ISSUED;
        private Limits lastLimits;
        private RuntimeException failGate;
        private RuntimeException failIssue;
        private RuntimeException failAudit;

        private final List<String> gatedCustomers = new ArrayList<>();

        @Override
        public Optional<NotQuotedReason> gate(String customerDigest, Limits limits) {
            lastLimits = limits;
            gatedCustomers.add(customerDigest);
            if (failGate != null) {
                throw failGate;
            }
            return gate;
        }

        @Override
        public void recordNotQuoted(ConversationId conversation, String customerDigest, NotQuotedReason reason,
                int lineCount, Instant now) {
            if (failAudit != null) {
                throw failAudit;
            }
            notQuoted.add(reason);
        }

        @Override
        public IssueResult issue(NewQuote quote, Limits limits) {
            if (failIssue != null) {
                throw failIssue;
            }
            if (issueResult == IssueResult.ISSUED) {
                issued.add(quote);
            }
            return issueResult;
        }

        @Override
        public Optional<StoredHandle> findHandle(String tokenHash) {
            return Optional.empty();
        }

        @Override
        public List<StoredLine> lines(long quoteId) {
            return List.of();
        }
    }
}
