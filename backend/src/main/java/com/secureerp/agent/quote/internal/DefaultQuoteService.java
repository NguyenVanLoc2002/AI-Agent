package com.secureerp.agent.quote.internal;

import com.secureerp.agent.identity.CustomerLinks;
import com.secureerp.agent.identity.spi.ErpCustomerLink;
import com.secureerp.agent.platform.Money;
import com.secureerp.agent.quote.QuoteOutcome;
import com.secureerp.agent.quote.QuoteRequest;
import com.secureerp.agent.quote.QuoteService;
import com.secureerp.agent.quote.spi.AvailabilityPort;
import com.secureerp.agent.quote.spi.CreditEligibilityPort;
import com.secureerp.agent.quote.spi.EligibilityStatus;
import com.secureerp.agent.quote.spi.PriceQuote;
import com.secureerp.agent.quote.spi.PricingPort;
import com.secureerp.agent.quote.spi.QuoteSourceUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Checks credit, price and stock through the quote ports, outside any transaction (no database
 * transaction is held during ERP calls), then stores the quote in one transaction. Logs carry the
 * outcome and line count only: never prices, stock, credit status or the reason (rule 10, BR-35).
 *
 * <p>Owner decisions (EP-06 review rounds, 2026-11-19):
 * <ul>
 *   <li>A-19: the ERP customer comes from identity ({@link CustomerLinks}); the ports never see the
 *       session reference;</li>
 *   <li>A-33: a business refusal ({@link NotQuotedReason#locksAndRetires()}) retires the customer's
 *       older ISSUED quote in the conversation;</li>
 *   <li>A-34: after a business refusal the customer (all conversations) gets no automatic quote for
 *       {@code apas.quote.lock-after-not-quoted} (24 h), and at most
 *       {@code apas.quote.max-issued-per-conversation-per-hour} (5, per customer) quotes are issued per
 *       hour. Both are checked before any ERP call and again under the issue lock. Requests made at the
 *       same time can all reach the ERP before the first refusal is stored (recorded residual).</li>
 * </ul>
 */
@Service
class DefaultQuoteService implements QuoteService {

    private static final Logger log = LoggerFactory.getLogger(DefaultQuoteService.class);
    static final Duration ISSUE_WINDOW = Duration.ofHours(1);

    private final PricingPort pricing;
    private final AvailabilityPort availability;
    private final CreditEligibilityPort credit;
    private final CustomerLinks links;
    private final QuoteStore store;
    private final QuoteProperties properties;
    private final QuoteTokens tokens = new QuoteTokens();
    private final Clock clock;

    DefaultQuoteService(PricingPort pricing, AvailabilityPort availability, CreditEligibilityPort credit,
            CustomerLinks links, QuoteStore store, QuoteProperties properties, Clock clock) {
        this.pricing = pricing;
        this.availability = availability;
        this.credit = credit;
        this.links = links;
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    /** Either the priced lines or the reason they cannot be quoted. */
    private record Evaluation(List<QuoteCalculator.PricedLine> lines, NotQuotedReason reason) {
    }

    @Override
    public QuoteOutcome request(QuoteRequest request) {
        Objects.requireNonNull(request, "request");
        Instant now = clock.instant();
        int lineCount = request.lines().size();
        QuoteStore.Limits limits = limits(now);
        String customer = request.customer() == null ? null : request.customer().bindingDigest();
        Optional<NotQuotedReason> gate;
        try {
            gate = store.gate(customer, limits);
        } catch (RuntimeException storeFailure) {
            log.warn("quote.request gate_failed exception={}", storeFailure.getClass().getSimpleName());
            gate = Optional.of(NotQuotedReason.STORE_UNAVAILABLE);
        }
        if (gate.isPresent()) {
            return notQuoted(request, customer, gate.get(), now);
        }
        Optional<ErpCustomerLink> erpCustomer;
        try {
            erpCustomer = links.erpLinkOf(request.customer());
        } catch (RuntimeException linkStoreFailure) {
            // Our own database, not the ERP (backend review 2 L-4).
            log.warn("quote.request link_failed exception={}", linkStoreFailure.getClass().getSimpleName());
            return notQuoted(request, customer, NotQuotedReason.STORE_UNAVAILABLE, now);
        }
        Evaluation evaluation;
        try {
            evaluation = evaluate(erpCustomer, request);
        } catch (QuoteSourceUnavailableException sourceDown) {
            log.warn("quote.request source_unavailable");
            evaluation = new Evaluation(List.of(), NotQuotedReason.SOURCE_UNAVAILABLE);
        } catch (RuntimeException unexpected) {
            // Fail closed, but keep a programming error distinguishable from an ERP outage (arch L5).
            log.warn("quote.request source_failed_unexpectedly exception={}", unexpected.getClass().getSimpleName());
            evaluation = new Evaluation(List.of(), NotQuotedReason.SOURCE_UNAVAILABLE);
        }
        if (evaluation.reason() != null) {
            return notQuoted(request, customer, evaluation.reason(), now);
        }
        // Every failure after the ERP checks is NOT_QUOTED as well, so the caller always gets the same
        // shape and the tool layer always hands off (EP-06 reviews: backend M-2, security M-3).
        String handle;
        try {
            QuoteCalculator.Calculation calculation = QuoteCalculator.calculate(evaluation.lines(),
                    Money.zero(properties.currency()), properties.moneyScale(), properties.rounding());
            handle = tokens.newHandle();
            QuoteStore.IssueResult result = store.issue(new QuoteStore.NewQuote(tokens.newPublicRef(),
                    request.conversation(), customer, properties.currency(),
                    properties.moneyScale(), properties.rounding(), calculation, QuoteTokens.hash(handle), now,
                    now.plus(properties.validity())), limits);
            switch (result) {
                case STALE -> {
                    return notQuoted(request, customer, NotQuotedReason.STALE_REQUEST, now);
                }
                case LOCKED -> {
                    return notQuoted(request, customer, NotQuotedReason.HANDED_OFF, now);
                }
                case LIMIT -> {
                    return notQuoted(request, customer, NotQuotedReason.REISSUE_LIMIT, now);
                }
                case FOREIGN -> {
                    log.warn("quote.request conversation_mismatch");
                    return notQuoted(request, customer, NotQuotedReason.CONVERSATION_MISMATCH, now);
                }
                case ISSUED -> {
                    // continue
                }
            }
        } catch (RuntimeException storeFailure) {
            log.warn("quote.request store_failed exception={}", storeFailure.getClass().getSimpleName());
            return notQuoted(request, customer, NotQuotedReason.STORE_UNAVAILABLE, now);
        }
        log.info("quote.request outcome=ISSUED lines={}", lineCount);
        return new QuoteOutcome.Issued(handle, lineCount);
    }

    private QuoteStore.Limits limits(Instant now) {
        return new QuoteStore.Limits(now.minus(properties.lockAfterNotQuoted()), now.minus(ISSUE_WINDOW),
                properties.maxIssuedPerConversationPerHour());
    }

    /**
     * Records the reason for staff and, for a business refusal, retires the older ISSUED quote (A-33).
     * A refusal that locks the customer also opens the handoff in the same transaction (A-43(s), A-48).
     * Best effort: NOT_QUOTED (and the handoff) must not depend on the database. If this write fails after
     * a business refusal, the customer is not locked (recorded residual A-43(t); the tool layer still hands
     * off).
     */
    private QuoteOutcome notQuoted(QuoteRequest request, String customer, NotQuotedReason reason, Instant now) {
        int lineCount = request.lines().size();
        try {
            store.recordNotQuoted(request.conversation(), customer, reason, lineCount, now);
        } catch (RuntimeException auditFailure) {
            log.warn("quote.request audit_failed exception={}", auditFailure.getClass().getSimpleName());
        }
        log.info("quote.request outcome=NOT_QUOTED lines={}", lineCount);
        return new QuoteOutcome.NotQuoted(lineCount);
    }

    private Evaluation evaluate(Optional<ErpCustomerLink> customer, QuoteRequest request) {
        if (customer.isEmpty()) {
            return new Evaluation(List.of(), NotQuotedReason.NO_CUSTOMER);
        }
        EligibilityStatus status = credit.check(customer.get());
        if (status != EligibilityStatus.ELIGIBLE) {
            return new Evaluation(List.of(), status == EligibilityStatus.NOT_ELIGIBLE
                    ? NotQuotedReason.CREDIT_NOT_ELIGIBLE : NotQuotedReason.CREDIT_REVIEW_REQUIRED);
        }
        List<QuoteCalculator.PricedLine> priced = new ArrayList<>();
        for (QuoteRequest.Line line : request.lines()) {
            Optional<PriceQuote> price = pricing.priceFor(customer.get(), line.sku());
            if (price.isEmpty()) {
                return new Evaluation(List.of(), NotQuotedReason.PRICE_NOT_FOUND);
            }
            if (!price.get().unitPrice().currency().equals(properties.currency())) {
                return new Evaluation(List.of(), NotQuotedReason.PRICE_CURRENCY_MISMATCH);
            }
            OptionalInt stock = availability.quantityAvailable(line.sku());
            if (stock.isEmpty() || stock.getAsInt() < line.quantity()) {
                return new Evaluation(List.of(), NotQuotedReason.INSUFFICIENT_STOCK);
            }
            priced.add(new QuoteCalculator.PricedLine(line.sku(), line.quantity(), price.get()));
        }
        return new Evaluation(priced, null);
    }
}
