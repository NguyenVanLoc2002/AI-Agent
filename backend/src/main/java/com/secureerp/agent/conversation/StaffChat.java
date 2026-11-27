package com.secureerp.agent.conversation;

import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.quote.staff.StaffQuotes;
import java.util.List;
import java.util.UUID;

/**
 * A staff member's side of a conversation they hold (UC-06, RBAC_MATRIX: SALES, only while the
 * handoff is claimed by them; A-15: access ends at resolve). Failures are {@code ApplicationException}
 * codes: 403 for anyone but SALES, 404 for a conversation the caller does not hold (another member's,
 * another team's, resolved, or missing: all the same answer).
 *
 * <p>Staff messages are stored with direction STAFF and never enter the model history (BR-36,
 * ADR-011): the history is built from customer messages only.
 *
 * <p>STAFF-MSG-ID (A-90): a staff message carries the client's event ID. The same ID with the same text again
 * is the message already stored (no second message, no second audit record); the ID of another message (other
 * text or a customer message) is 409.
 */
public interface StaffChat {

    List<Chat.ChatMessage> messages(SessionIdentity staff, ConversationId conversation);

    void post(SessionIdentity staff, ConversationId conversation, UUID clientEventId, String text);

    /**
     * The newest quote of the conversation with its prices (EP-08b; RBAC_MATRIX "Xem quote (có giá)").
     * Read only: no confirmation token. 404 as for {@link #messages}, and also when it has no quote.
     */
    StaffQuotes.StaffQuoteView quote(SessionIdentity staff, ConversationId conversation);
}
