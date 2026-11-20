package com.secureerp.agent.conversation.internal;

import com.secureerp.agent.handoff.ConversationNotFollowing;
import com.secureerp.agent.handoff.HandoffClaimed;
import com.secureerp.agent.handoff.HandoffResolved;
import com.secureerp.agent.platform.ConversationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The conversation follows its handoff (DATA_FLOW §3): claimed → HUMAN_ACTIVE; resolved → ACTIVE_BOT
 * (BR-22: only staff unlock the bot), or back to HANDOFF_PENDING when a follow-up handoff was opened.
 * Synchronous, inside the claiming or resolving transaction. A conversation that exists but is not in
 * the expected state fails the change ({@link ConversationNotFollowing} rolls the handoff back), so the
 * two never drift apart.
 */
@Component
class HandoffStateListener {

    private static final Logger log = LoggerFactory.getLogger(HandoffStateListener.class);

    private final ConversationStore store;

    HandoffStateListener(ConversationStore store) {
        this.store = store;
    }

    @EventListener
    void on(HandoffClaimed claimed) {
        require(store.markHumanActive(claimed.conversation()), claimed.conversation(), "CLAIMED");
    }

    @EventListener
    void on(HandoffResolved resolved) {
        boolean followed = resolved.followUpOpen() ? store.markWaitingForStaffAgain(resolved.conversation())
                : store.markBotActive(resolved.conversation());
        require(followed, resolved.conversation(), "RESOLVED");
    }

    private void require(boolean followed, ConversationId conversation, String change) {
        if (!followed && store.exists(conversation)) {
            log.error("conversation.handoff_state not_following change={}", change);
            throw new ConversationNotFollowing();
        }
    }
}
