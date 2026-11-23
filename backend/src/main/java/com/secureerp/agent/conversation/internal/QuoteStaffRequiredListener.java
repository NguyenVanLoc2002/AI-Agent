package com.secureerp.agent.conversation.internal;

import com.secureerp.agent.handoff.HandoffReason;
import com.secureerp.agent.handoff.Handoffs;
import com.secureerp.agent.quote.QuoteStaffRequired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hands a conversation to staff when the confirmation of its quote needs them (EP-07; DATA_FLOW §2
 * steps 3 and 6, §3) or a quote request was refused for a reason that locks the customer (A-43(s)).
 * Synchronous and transactional: it joins the transaction that changed the quote or stored the refusal,
 * so the bot lock, the handoff record and the quote status or refusal commit together (same rule as
 * {@code ChatService}: lock and record in one transaction). A failure here fails the quote change.
 */
@Component
class QuoteStaffRequiredListener {

    private static final Logger log = LoggerFactory.getLogger(QuoteStaffRequiredListener.class);

    private final ConversationStore store;
    private final Handoffs handoffs;
    private final TransactionTemplate transactions;

    QuoteStaffRequiredListener(ConversationStore store, Handoffs handoffs, TransactionTemplate transactions) {
        this.store = store;
        this.handoffs = handoffs;
        this.transactions = transactions;
    }

    @EventListener
    void on(QuoteStaffRequired event) {
        HandoffReason reason = reason(event.reason());
        if (!store.boundTo(event.conversation(), event.customerDigest())) {
            // No handoff on a conversation that is not this customer's: staff would get access to it
            // without the bot being locked (review 2026-11-19).
            log.error("conversation.handoff refused code=CUSTOMER_NOT_BOUND reason={}", reason);
            return;
        }
        transactions.executeWithoutResult(tx -> {
            handoffs.open(event.conversation(), reason, store.teamOf(event.conversation()).orElse(null));
            store.markHandoffPendingForCustomer(event.conversation(), event.customerDigest());
        });
        // The trigger, not "confirmation": a refusal is logged as such (security review L-3); no internal reason.
        log.info("conversation.handoff quote trigger={} reason={}", event.reason(), reason);
    }

    /**
     * DATA_FLOW §2: a refused quote and a blocked confirmation are QUOTE_NOT_ISSUED (details internal), an
     * unknown outcome ERP_UNKNOWN. The refusal uses the reason {@code ChatService} gives at the end of the
     * turn, so that later open adds no follow-up while the handoff is still open.
     */
    static HandoffReason reason(QuoteStaffRequired.Reason reason) {
        return switch (reason) {
            case NOT_QUOTED -> HandoffReason.QUOTE_NOT_ISSUED;
            case CONFIRMATION_BLOCKED -> HandoffReason.QUOTE_NOT_ISSUED;
            case SOURCE_UNAVAILABLE -> HandoffReason.CATALOG_UNAVAILABLE;
            case ORDER_OUTCOME_UNKNOWN -> HandoffReason.ERP_UNKNOWN;
            case ORDER_FAILED -> HandoffReason.ORDER_NOT_CREATED;
        };
    }
}
