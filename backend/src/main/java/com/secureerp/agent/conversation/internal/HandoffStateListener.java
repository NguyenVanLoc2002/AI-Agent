package com.secureerp.agent.conversation.internal;

import com.secureerp.agent.handoff.ConversationNotFollowing;
import com.secureerp.agent.handoff.HandoffClaimed;
import com.secureerp.agent.handoff.HandoffResolved;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.quote.staff.QuoteLocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The conversation follows its handoff (DATA_FLOW §3): claimed → HUMAN_ACTIVE; resolved → ACTIVE_BOT
 * (BR-22: only staff unlock the bot), or back to HANDOFF_PENDING when a follow-up handoff was opened.
 * Synchronous, inside the claiming or resolving transaction. A conversation that exists but is not in
 * the expected state fails the change ({@link ConversationNotFollowing} rolls the handoff back), so the
 * two never drift apart. A resolve without a follow-up also lifts the conversation's A-34 quote lock
 * ({@link QuoteLocks}, EP-08b).
 */
@Component
class HandoffStateListener {

    private static final Logger log = LoggerFactory.getLogger(HandoffStateListener.class);

    private final ConversationStore store;
    private final QuoteLocks quoteLocks;

    HandoffStateListener(ConversationStore store, QuoteLocks quoteLocks) {
        this.store = store;
        this.quoteLocks = quoteLocks;
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
        if (!resolved.followUpOpen()) {
            // EP-08b (A-34 lift, owner 2026-11-20): staff dealt with the conversation; its earlier business
            // refusals no longer block automatic quotes. Same transaction as the resolve.
            quoteLocks.liftAfterStaffResolved(resolved.conversation());
        }
    }

    private void require(boolean followed, ConversationId conversation, String change) {
        if (!followed && store.exists(conversation)) {
            log.error("conversation.handoff_state not_following change={}", change);
            throw new ConversationNotFollowing();
        }
    }
}
