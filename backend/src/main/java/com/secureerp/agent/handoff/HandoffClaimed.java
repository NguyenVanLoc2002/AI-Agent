package com.secureerp.agent.handoff;

import com.secureerp.agent.platform.ConversationId;
import java.util.Objects;

/**
 * A staff member took a conversation. Published inside the claiming transaction, so the conversation
 * becomes HUMAN_ACTIVE with it or not at all (DATA_FLOW §3).
 */
public record HandoffClaimed(ConversationId conversation) {

    public HandoffClaimed {
        Objects.requireNonNull(conversation, "conversation");
    }
}
