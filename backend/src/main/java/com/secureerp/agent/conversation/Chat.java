package com.secureerp.agent.conversation;

import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.platform.ConversationId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The conversation module's API for its web adapter (UC-01, DATA_FLOW §1). Every call takes the session
 * identity, never a customer value from the request (ARCH-08b); failures are {@code ApplicationException}
 * codes (404 for someone else's or a missing conversation, 403, 409 while another turn of the
 * conversation is running, 429).
 */
public interface Chat {

    ConversationId create(SessionIdentity identity);

    ChatTurn post(SessionIdentity identity, ConversationId conversation, UUID clientEventId, String text);

    List<ChatMessage> messages(SessionIdentity identity, ConversationId conversation);

    /**
     * A stored customer, bot or staff message as the owner (or the staff member holding the conversation) sees it:
     * sanitized or validated text only. {@code sentAt} is when the message was stored, in UTC and truncated to
     * whole seconds (UX13-MSG-TIME, A-61); the display time zone is the client's concern. No message ID.
     */
    record ChatMessage(String direction, String text, Instant sentAt) {
    }
}
