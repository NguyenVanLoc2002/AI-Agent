package com.secureerp.agent.quote.internal;

import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.identity.AuthenticatedCustomerRef;
import com.secureerp.agent.identity.CustomerLinks;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.platform.Money;
import com.secureerp.agent.quote.QuoteCard;
import com.secureerp.agent.quote.QuoteCards;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Renders a quote card only for a handle that matches every binding (ADR-008). The log line carries
 * the outcome only: never the handle, its hash, the quote reference or an amount.
 */
@Service
class DefaultQuoteCards implements QuoteCards {

    private static final Logger log = LoggerFactory.getLogger(DefaultQuoteCards.class);
    private static final String ISSUED = "ISSUED";

    private final QuoteStore store;
    private final ConfirmationStore confirmations;
    private final CustomerLinks links;
    private final QuoteTokens tokens = new QuoteTokens();
    private final Clock clock;

    DefaultQuoteCards(QuoteStore store, ConfirmationStore confirmations, CustomerLinks links, Clock clock) {
        this.store = store;
        this.confirmations = confirmations;
        this.links = links;
        this.clock = clock;
    }

    @Override
    public Optional<QuoteCard> render(String quoteHandle, ConversationId conversation,
            AuthenticatedCustomerRef customer) {
        Objects.requireNonNull(conversation, "conversation");
        Optional<QuoteCard> card;
        try {
            card = resolve(quoteHandle, conversation, customer, clock.instant());
        } catch (RuntimeException invalid) {
            // A stored snapshot that no longer fits the money rules (or a store failure) renders nothing
            // (backend review 2 L-3): uniform empty result, class name only in the log.
            log.warn("quote.render outcome=INVALID exception={}", invalid.getClass().getSimpleName());
            return Optional.empty();
        }
        log.info("quote.render outcome={}", card.isPresent() ? "RENDERED" : "NOT_FOUND");
        return card;
    }

    private Optional<QuoteCard> resolve(String quoteHandle, ConversationId conversation,
            AuthenticatedCustomerRef customer, Instant now) {
        if (quoteHandle == null || customer == null || !QuoteTokens.HANDLE.matcher(quoteHandle).matches()) {
            return Optional.empty();
        }
        Optional<QuoteStore.StoredHandle> found = store.findHandle(QuoteTokens.hash(quoteHandle));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        QuoteStore.StoredHandle handle = found.get();
        boolean valid = handle.conversationId().equals(conversation.value())
                && handle.customerDigest().equals(customer.bindingDigest())
                && handle.revokedAt() == null
                && handle.handleExpiresAt().isAfter(now)
                && handle.validUntil().isAfter(now)
                && ISSUED.equals(handle.status())
                && handle.quoteVersion() == handle.handleVersion();
        // A revoked customer link stops rendering at once (security review 2 L-5).
        if (!valid || links.erpLinkOf(customer).isEmpty()) {
            return Optional.empty();
        }
        List<QuoteCard.Line> lines = store.lines(handle.quoteId()).stream()
                .map(line -> line(line.sku(), line.quantity(), line.unitPrice(), line.lineTotal(),
                        handle.moneyScale(), handle.currency()))
                .toList();
        // EP-07: the card carries the token of its confirm control; only its hash is stored, and only
        // while the quote is still ISSUED (a quote confirmed in the meantime renders nothing).
        String confirmationToken = tokens.newConfirmationToken();
        if (!confirmations.armConfirmation(handle.quoteId(), QuoteTokens.hash(confirmationToken))) {
            return Optional.empty();
        }
        return Optional.of(new QuoteCard(handle.publicRef(), handle.quoteVersion(), lines,
                exact(handle.total(), handle.moneyScale(), handle.currency()), handle.validUntil(),
                List.of(QuoteCard.NOTE_VAT, QuoteCard.NOTE_FITMENT), confirmationToken));
    }

    static QuoteCard.Line line(String sku, int quantity, BigDecimal unitPrice, BigDecimal lineTotal, int moneyScale,
            Currency currency) {
        return new QuoteCard.Line(new Sku(sku), quantity, unitPrice(unitPrice, moneyScale, currency),
                exact(lineTotal, moneyScale, currency));
    }

    /** Totals were rounded to the quote's money scale when issued; NUMERIC(19,4) only pads them (review L4). */
    static Money exact(BigDecimal stored, int moneyScale, Currency currency) {
        return Money.of(stored.setScale(moneyScale, RoundingMode.UNNECESSARY), currency);
    }

    /** The ERP unit price keeps its own precision, shown with at least the quote's money scale. */
    static Money unitPrice(BigDecimal stored, int moneyScale, Currency currency) {
        BigDecimal plain = stored.stripTrailingZeros();
        return Money.of(plain.scale() < moneyScale ? plain.setScale(moneyScale) : plain, currency);
    }
}
