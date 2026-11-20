package com.secureerp.agent.quote.staff;

import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.platform.Money;
import com.secureerp.agent.quote.QuoteCard;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The quote of a conversation as the staff member holding it sees it (EP-08b; RBAC_MATRIX "Xem quote
 * (có giá)": SALES, assigned conversation; owner decision 2026-11-20: only while holding the handoff,
 * A-15). Read only: no confirmation token is issued and nothing is written. RESTRICTED (customer
 * prices): never an egress type. The internal reason of a NOT_QUOTED stays out (owner 2026-11-20, A-43).
 *
 * <p>This module cannot see handoffs: the caller must have checked that the staff member holds the
 * conversation ({@code StaffChat}).
 */
public interface StaffQuotes {

    /**
     * @param status the stored ADR-007 status ({@code EXPIRED} for an ISSUED quote past its validity)
     */
    record StaffQuoteView(String quoteRef, int version, String status, List<QuoteCard.Line> lines, Money total,
            Instant validUntil, List<String> notes) {

        public StaffQuoteView {
            Objects.requireNonNull(quoteRef, "quoteRef");
            Objects.requireNonNull(status, "status");
            lines = List.copyOf(lines);
            Objects.requireNonNull(total, "total");
            Objects.requireNonNull(validUntil, "validUntil");
            notes = List.copyOf(notes);
        }

        @Override
        public String toString() {
            return "StaffQuoteView[" + status + ", " + lines.size() + " lines]";
        }
    }

    /** The newest quote (latest version) of the conversation; empty when it has none. */
    Optional<StaffQuoteView> newestOf(ConversationId conversation);
}
