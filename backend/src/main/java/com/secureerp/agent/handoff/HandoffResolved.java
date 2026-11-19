package com.secureerp.agent.handoff;

import com.secureerp.agent.platform.ConversationId;
import java.util.Objects;

/**
 * The staff member holding a conversation gave it back. Published inside the resolving transaction,
 * so the conversation follows with it or not at all (BR-22: only staff unlock the bot).
 *
 * @param followUpOpen a new handoff was opened in the same transaction, for a reason raised while the
 *                     resolved one was active; the bot must stay locked and wait for staff again
 */
public record HandoffResolved(ConversationId conversation, boolean followUpOpen) {

    public HandoffResolved {
        Objects.requireNonNull(conversation, "conversation");
    }
}
