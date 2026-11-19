package com.secureerp.agent.quote.internal;

import com.secureerp.agent.order.DraftOrderMirrorRefused;
import com.secureerp.agent.order.DraftOrderStateChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the quote status in step with its draft order request when order changes it outside a
 * confirmation call: staff reconciliation and requests that timed out (ADR-007). Runs synchronously
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
        switch (store.mirror(change.quoteRef(), change.quoteVersion(), target)) {
            case CHANGED, UNCHANGED -> { }
            case NO_QUOTE -> log.warn("quote.mirror outcome=NO_QUOTE target={}", target);
            case REFUSED -> {
                log.error("quote.mirror outcome=REFUSED target={}", target);
                throw new DraftOrderMirrorRefused();
            }
        }
    }
}
