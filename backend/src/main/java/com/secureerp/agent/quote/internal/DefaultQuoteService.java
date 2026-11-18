package com.secureerp.agent.quote.internal;

import com.secureerp.agent.identity.AuthenticatedCustomerRef;
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
 */
@Service
class DefaultQuoteService implements QuoteService {

    private static final Logger log = LoggerFactory.getLogger(DefaultQuoteService.class);

    private final PricingPort pricing;
    private final AvailabilityPort availability;
    private final CreditEligibilityPort credit;
    private final QuoteStore store;
    private final QuoteProperties properties;
    private final QuoteTokens tokens = new QuoteTokens();
    private final Clock clock;

    DefaultQuoteService(PricingPort pricing, AvailabilityPort availability, CreditEligibilityPort credit,
            QuoteStore store, QuoteProperties properties, Clock clock) {
        this.pricing = pricing;
        this.availability = availability;
        this.credit = credit;
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
        Evaluation evaluation;
        try {
            evaluation = evaluate(request);
        } catch (QuoteSourceUnavailableException sourceDown) {
            log.warn("quote.request source_unavailable");
            evaluation = new Evaluation(List.of(), NotQuotedReason.SOURCE_UNAVAILABLE);
        } catch (RuntimeException unexpected) {
            // Fail closed, but keep a programming error distinguishable from an ERP outage (arch L5).
            log.warn("quote.request source_failed_unexpectedly exception={}", unexpected.getClass().getSimpleName());
            evaluation = new Evaluation(List.of(), NotQuotedReason.SOURCE_UNAVAILABLE);
        }
        if (evaluation.reason() != null) {
            return notQuoted(request, evaluation.reason(), now);
        }
        // Every failure after the ERP checks is NOT_QUOTED as well, so the caller always gets the same
        // shape and the tool layer always hands off (EP-06 reviews: backend M-2, security M-3).
        String handle;
        try {
            QuoteCalculator.Calculation calculation = QuoteCalculator.calculate(evaluation.lines(),
                    Money.zero(properties.currency()), properties.moneyScale(), properties.rounding());
            handle = tokens.newHandle();
            boolean issued = store.issue(new QuoteStore.NewQuote(tokens.newPublicRef(), request.conversation(),
                    request.customer().bindingDigest(), properties.currency(), properties.moneyScale(),
                    properties.rounding(), calculation, QuoteTokens.hash(handle), now,
                    now.plus(properties.validity())));
            if (!issued) {
                return notQuoted(request, NotQuotedReason.STALE_REQUEST, now);
            }
        } catch (RuntimeException storeFailure) {
            log.warn("quote.request store_failed exception={}", storeFailure.getClass().getSimpleName());
            return notQuoted(request, NotQuotedReason.STORE_UNAVAILABLE, now);
        }
        log.info("quote.request outcome=ISSUED lines={}", lineCount);
        return new QuoteOutcome.Issued(handle, lineCount);
    }

    /** The audit row is best effort: NOT_QUOTED (and the handoff) must not depend on the database. */
    private QuoteOutcome notQuoted(QuoteRequest request, NotQuotedReason reason, Instant now) {
        int lineCount = request.lines().size();
        try {
            store.recordNotQuoted(request.conversation(), reason, lineCount, now);
        } catch (RuntimeException auditFailure) {
            log.warn("quote.request audit_failed exception={}", auditFailure.getClass().getSimpleName());
        }
        log.info("quote.request outcome=NOT_QUOTED lines={}", lineCount);
        return new QuoteOutcome.NotQuoted(lineCount);
    }

    private Evaluation evaluate(QuoteRequest request) {
        AuthenticatedCustomerRef customer = request.customer();
        if (customer == null) {
            return new Evaluation(List.of(), NotQuotedReason.NO_CUSTOMER);
        }
        EligibilityStatus status = credit.check(customer);
        if (status != EligibilityStatus.ELIGIBLE) {
            return new Evaluation(List.of(), status == EligibilityStatus.NOT_ELIGIBLE
                    ? NotQuotedReason.CREDIT_NOT_ELIGIBLE : NotQuotedReason.CREDIT_REVIEW_REQUIRED);
        }
        List<QuoteCalculator.PricedLine> priced = new ArrayList<>();
        for (QuoteRequest.Line line : request.lines()) {
            Optional<PriceQuote> price = pricing.priceFor(customer, line.sku());
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
