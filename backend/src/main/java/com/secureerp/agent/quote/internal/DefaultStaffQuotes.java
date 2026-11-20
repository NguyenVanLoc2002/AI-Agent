package com.secureerp.agent.quote.internal;

import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.quote.QuoteCard;
import com.secureerp.agent.quote.staff.StaffQuotes;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Read-only staff view of a conversation's newest quote (EP-08b). Unlike the customer's read it never
 * arms a confirmation token and never repairs a status. Logs carry the outcome only.
 */
@Service
class DefaultStaffQuotes implements StaffQuotes {

    private static final Logger log = LoggerFactory.getLogger(DefaultStaffQuotes.class);

    private final ConfirmationStore store;
    private final Clock clock;

    DefaultStaffQuotes(ConfirmationStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    public Optional<StaffQuoteView> newestOf(ConversationId conversation) {
        Objects.requireNonNull(conversation, "conversation");
        Optional<ConfirmationStore.QuoteRow> found = store.newestInConversation(conversation.value());
        if (found.isEmpty()) {
            log.info("quote.staff_read outcome=NOT_FOUND");
            return Optional.empty();
        }
        ConfirmationStore.QuoteRow row = found.get();
        String status = row.status() == QuoteStatus.ISSUED && !row.validUntil().isAfter(clock.instant())
                ? QuoteStatus.EXPIRED.name() : row.status().name();
        try {
            List<QuoteCard.Line> lines = store.lines(row.id()).stream()
                    .map(line -> DefaultQuoteCards.line(line.sku(), line.quantity(), line.unitPrice(),
                            line.lineTotal(), row.moneyScale(), row.currency()))
                    .toList();
            StaffQuoteView view = new StaffQuoteView(row.publicRef(), row.version(), status, lines,
                    DefaultQuoteCards.exact(row.total(), row.moneyScale(), row.currency()), row.validUntil(),
                    List.of(QuoteCard.NOTE_VAT, QuoteCard.NOTE_FITMENT));
            log.info("quote.staff_read outcome=RENDERED");
            return Optional.of(view);
        } catch (ArithmeticException | IllegalArgumentException invalid) {
            // A stored snapshot that no longer fits the money rules shows nothing (as the customer's read).
            log.warn("quote.staff_read outcome=INVALID exception={}", invalid.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
