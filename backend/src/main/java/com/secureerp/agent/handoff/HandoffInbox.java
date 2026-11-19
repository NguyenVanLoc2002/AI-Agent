package com.secureerp.agent.handoff;

import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.platform.ConversationId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The staff inbox (UC-06, RBAC_MATRIX: SALES, own team only). Failures are
 * {@code ApplicationException} codes: 403 for anyone but SALES (an administrator is not a business
 * reader, BR-24), 404 for a handoff of another team or a missing one (same answer, AC-06.1), 409 when
 * the handoff is not in a state that allows the action.
 */
public interface HandoffInbox {

    enum Status {
        OPEN,
        CLAIMED,
        RESOLVED
    }

    /**
     * One handoff as the team sees it. No customer identity and no message content: the reason is an
     * internal code, the conversation an opaque id.
     *
     * @param mine whether the calling staff member holds it
     * @param followUpReasonCode another reason raised while this handoff was active, or {@code null};
     *                           resolving opens a new handoff with it
     */
    record Item(long handoffId, ConversationId conversation, String reasonCode, Status status, Instant openedAt,
            boolean mine, String followUpReasonCode) {

        public Item {
            Objects.requireNonNull(conversation, "conversation");
            Objects.requireNonNull(reasonCode, "reasonCode");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(openedAt, "openedAt");
        }
    }

    /** Handoffs of the staff member's team that are not resolved, oldest first. */
    List<Item> list(SessionIdentity staff);

    /** OPEN → CLAIMED by the caller; the same claim again is accepted, another member's claim is 409. */
    Item claim(SessionIdentity staff, long handoffId);

    /**
     * CLAIMED by the caller → RESOLVED; ends the caller's access to the conversation and unlocks the bot,
     * unless another reason was raised meanwhile: then a new handoff is opened and the bot stays locked.
     */
    Item resolve(SessionIdentity staff, long handoffId);
}
