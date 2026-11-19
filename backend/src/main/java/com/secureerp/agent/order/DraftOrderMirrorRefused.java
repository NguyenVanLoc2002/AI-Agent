package com.secureerp.agent.order;

/**
 * Thrown by a listener of {@link DraftOrderStateChanged} when the quote cannot follow the request's
 * new state. It rolls the request's change back, so the two never drift apart silently; order
 * answers a conflict to the caller. Carries no message beyond a fixed code.
 */
public final class DraftOrderMirrorRefused extends RuntimeException {

    public DraftOrderMirrorRefused() {
        super("DRAFT_ORDER_MIRROR_REFUSED", null, false, false);
    }
}
