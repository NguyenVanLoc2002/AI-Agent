package com.secureerp.agent.conversation;

import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.platform.ConversationId;
import java.util.List;

/**
 * A staff member's side of a conversation they hold (UC-06, RBAC_MATRIX: SALES, only while the
 * handoff is claimed by them; A-15: access ends at resolve). Failures are {@code ApplicationException}
 * codes: 403 for anyone but SALES, 404 for a conversation the caller does not hold (another member's,
 * another team's, resolved, or missing: all the same answer).
 *
 * <p>Staff messages are stored with direction STAFF and never enter the model history (BR-36,
 * ADR-011): the history is built from customer messages only.
 */
public interface StaffChat {

    List<Chat.ChatMessage> messages(SessionIdentity staff, ConversationId conversation);

    void post(SessionIdentity staff, ConversationId conversation, String text);
}
