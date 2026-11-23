package com.secureerp.agent.conversation.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.agent.AgentReply;
import com.secureerp.agent.handoff.HandoffReason;
import com.secureerp.agent.quote.QuoteStaffRequired;
import org.junit.jupiter.api.Test;

/** DATA_FLOW §2: the handoff reason staff see for each reason a quote needs them (EP-07; A-43(s)). */
class QuoteStaffRequiredListenerTest {

    @Test
    void aRefusedQuoteIsHandedOffWithTheReasonOfTheEndOfTheTurn() {
        // The same reason ChatService gives (AgentReply QUOTE_NOT_ISSUED): its later open adds no follow-up.
        assertThat(QuoteStaffRequiredListener.reason(QuoteStaffRequired.Reason.NOT_QUOTED))
                .isEqualTo(HandoffReason.QUOTE_NOT_ISSUED)
                .isEqualTo(ChatService.reason(AgentReply.Reason.QUOTE_NOT_ISSUED));
    }

    @Test
    void everyConfirmationReasonKeepsItsHandoffReason() {
        assertThat(QuoteStaffRequiredListener.reason(QuoteStaffRequired.Reason.CONFIRMATION_BLOCKED))
                .isEqualTo(HandoffReason.QUOTE_NOT_ISSUED);
        assertThat(QuoteStaffRequiredListener.reason(QuoteStaffRequired.Reason.SOURCE_UNAVAILABLE))
                .isEqualTo(HandoffReason.CATALOG_UNAVAILABLE);
        assertThat(QuoteStaffRequiredListener.reason(QuoteStaffRequired.Reason.ORDER_OUTCOME_UNKNOWN))
                .isEqualTo(HandoffReason.ERP_UNKNOWN);
        assertThat(QuoteStaffRequiredListener.reason(QuoteStaffRequired.Reason.ORDER_FAILED))
                .isEqualTo(HandoffReason.ORDER_NOT_CREATED);
    }
}
