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
 * steps 3 and 6, §3). Synchronous and transactional: it joins the transaction that changed the quote,
 * so the bot lock, the handoff record and the quote status commit together (same rule as
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
        transactions.executeWithoutResult(tx -> {
            store.markHandoffPendingForCustomer(event.conversation(), event.customerDigest());
            handoffs.open(event.conversation(), reason);
        });
        log.info("conversation.handoff quote_confirmation reason={}", reason);
    }

    /** DATA_FLOW §2: a blocked confirmation is QUOTE_NOT_ISSUED (details internal), an unknown outcome ERP_UNKNOWN. */
    static HandoffReason reason(QuoteStaffRequired.Reason reason) {
        return switch (reason) {
            case CONFIRMATION_BLOCKED -> HandoffReason.QUOTE_NOT_ISSUED;
            case SOURCE_UNAVAILABLE -> HandoffReason.CATALOG_UNAVAILABLE;
            case ORDER_OUTCOME_UNKNOWN -> HandoffReason.ERP_UNKNOWN;
            case ORDER_FAILED -> HandoffReason.ORDER_NOT_CREATED;
        };
    }
}
