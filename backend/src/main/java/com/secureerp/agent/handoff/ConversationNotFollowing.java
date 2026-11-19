package com.secureerp.agent.handoff;

/**
 * Thrown by a listener of {@link HandoffClaimed} / {@link HandoffResolved} when the conversation
 * exists but is not in the state the handoff change needs. It rolls the handoff change back, so a
 * handoff and its conversation never drift apart; the inbox answers a conflict.
 */
public final class ConversationNotFollowing extends RuntimeException {

    public ConversationNotFollowing() {
        super("CONVERSATION_NOT_FOLLOWING", null, false, false);
    }
}
