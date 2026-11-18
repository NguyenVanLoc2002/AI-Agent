package com.secureerp.agent.quote.internal;

import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.identity.AuthenticatedCustomerRef;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.platform.Money;
import com.secureerp.agent.quote.QuoteCard;
import com.secureerp.agent.quote.QuoteCards;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
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
    private final Clock clock;

    DefaultQuoteCards(QuoteStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    public Optional<QuoteCard> render(String quoteHandle, ConversationId conversation,
            AuthenticatedCustomerRef customer) {
        Objects.requireNonNull(conversation, "conversation");
        Optional<QuoteCard> card = resolve(quoteHandle, conversation, customer, clock.instant());
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
        if (!valid) {
            return Optional.empty();
        }
        List<QuoteCard.Line> lines = store.lines(handle.quoteId()).stream()
                .map(line -> new QuoteCard.Line(new Sku(line.sku()), line.quantity(),
                        unitPrice(line.unitPrice(), handle), exact(line.lineTotal(), handle)))
                .toList();
        return Optional.of(new QuoteCard(handle.publicRef(), handle.quoteVersion(), lines,
                exact(handle.total(), handle), handle.validUntil(),
                List.of(QuoteCard.NOTE_VAT, QuoteCard.NOTE_FITMENT)));
    }

    /** Totals were rounded to the quote's money scale when issued; NUMERIC(19,4) only pads them (review L4). */
    private static Money exact(BigDecimal stored, QuoteStore.StoredHandle handle) {
        return Money.of(stored.setScale(handle.moneyScale(), RoundingMode.UNNECESSARY), handle.currency());
    }

    /** The ERP unit price keeps its own precision, shown with at least the quote's money scale. */
    private static Money unitPrice(BigDecimal stored, QuoteStore.StoredHandle handle) {
        BigDecimal plain = stored.stripTrailingZeros();
        return Money.of(plain.scale() < handle.moneyScale() ? plain.setScale(handle.moneyScale()) : plain,
                handle.currency());
    }
}
