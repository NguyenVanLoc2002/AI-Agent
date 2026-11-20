package com.secureerp.agent.quote.internal;

import com.secureerp.agent.order.DraftOrderMirrorRefused;
import com.secureerp.agent.order.DraftOrderStateChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the quote status in step with its draft order request when order changes it outside a
 * confirmation call: staff reconciliation, requests that timed out and the staff decision (EP-08b) (ADR-007). Runs synchronously
 * inside order's transaction, so the request and the quote change together or not at all: when the
 * quote is in a status that cannot follow, this fails and order's change is rolled back, rather than
 * leaving the two silently apart.
 */
@Component
class DraftOrderMirror {

    private static final Logger log = LoggerFactory.getLogger(DraftOrderMirror.class);

    private final ConfirmationStore store;

    DraftOrderMirror(ConfirmationStore store) {
        this.store = store;
    }

    @EventListener
    void on(DraftOrderStateChanged change) {
        QuoteStatus target = QuoteStatus.mirrorOf(change.state());
        boolean staffDecision = target == QuoteStatus.STAFF_APPROVED || target == QuoteStatus.REJECTED;
        if (staffDecision) {
            // EP-08b reviews (backend M2): the request was created but the quote may still show the order as
            // being sent or unknown (an earlier mirror failed and was only logged). Catch it up first, in the
            // same transaction; both moves are legal ADR-007 transitions.
            follow(change, QuoteStatus.DRAFT_ORDER_CREATED, true);
        }
        follow(change, target, staffDecision);
    }

    /** @param failOnMissing a staff decision needs its quote: without one it is refused (rule 10, fail closed) */
    private void follow(DraftOrderStateChanged change, QuoteStatus target, boolean failOnMissing) {
        switch (store.mirror(change.quoteRef(), change.quoteVersion(), target)) {
            case CHANGED, UNCHANGED -> { }
            case NO_QUOTE -> {
                log.warn("quote.mirror outcome=NO_QUOTE target={}", target);
                if (failOnMissing) {
                    throw new DraftOrderMirrorRefused();
                }
            }
            case REFUSED -> {
                log.error("quote.mirror outcome=REFUSED target={}", target);
                throw new DraftOrderMirrorRefused();
            }
        }
    }
}
