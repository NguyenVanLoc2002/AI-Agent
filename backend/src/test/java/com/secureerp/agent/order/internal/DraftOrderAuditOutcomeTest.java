package com.secureerp.agent.order.internal;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.secureerp.agent.audit.AuditActor;
import com.secureerp.agent.audit.AuditEvent;
import com.secureerp.agent.order.DraftOrderState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * EP-09b round-2 review L2: every draft order state is a valid audit outcome code (staff decision and
 * reconciliation record the target state), so building the event cannot fail inside the change.
 */
class DraftOrderAuditOutcomeTest {

    private static final AuditActor STAFF = new AuditActor("usr_DraftOrderAuditTestAAA", "SALES");

    @ParameterizedTest
    @EnumSource(DraftOrderState.class)
    void everyStateIsAnAuditCode(DraftOrderState state) {
        assertThatCode(() -> new AuditEvent(AuditEvent.Action.STAFF_ORDER_DECIDED, AuditEvent.Resource.DRAFT_ORDER,
                state.name(), STAFF)).doesNotThrowAnyException();
    }
}
