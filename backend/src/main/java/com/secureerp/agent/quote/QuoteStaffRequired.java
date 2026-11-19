package com.secureerp.agent.quote;

import com.secureerp.agent.platform.ConversationId;
import java.util.Objects;

/**
 * A quote's confirmation needs staff (EP-07; DATA_FLOW §2 steps 3 and 6). Published inside the
 * transaction that changed the quote, so the conversation is locked for the bot and the handoff is
 * recorded atomically with it. The reason is internal (staff only); it never reaches the customer or
 * the model.
 *
 * @param customerDigest digest of the customer the quote belongs to; the listener changes the
 *                       conversation only when it is bound to the same customer
 */
public record QuoteStaffRequired(ConversationId conversation, String customerDigest, Reason reason) {

    public enum Reason {
        /** The confirmation re-check failed on stock, credit or price availability (BR-38). */
        CONFIRMATION_BLOCKED,
        /** The ERP could not be asked for the re-check. */
        SOURCE_UNAVAILABLE,
        /** The ERP outcome of the draft order is unknown (BR-19). */
        ORDER_OUTCOME_UNKNOWN,
        /** The ERP refused the draft order, or reconciliation found none. */
        ORDER_FAILED
    }

    public QuoteStaffRequired {
        Objects.requireNonNull(conversation, "conversation");
        Objects.requireNonNull(customerDigest, "customerDigest");
        Objects.requireNonNull(reason, "reason");
    }

    @Override
    public String toString() {
        return "QuoteStaffRequired[" + reason + "]";
    }
}
