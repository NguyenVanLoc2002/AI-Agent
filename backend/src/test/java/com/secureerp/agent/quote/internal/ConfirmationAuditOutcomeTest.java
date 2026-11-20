package com.secureerp.agent.quote.internal;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.secureerp.agent.audit.AuditActor;
import com.secureerp.agent.audit.AuditEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * EP-09b round-2 review L2: every confirmation outcome is a valid audit outcome code, so building the event in
 * {@code finish()} can never throw after a confirmation was committed.
 */
class ConfirmationAuditOutcomeTest {

    private static final AuditActor CUSTOMER = new AuditActor("usr_ConfirmationAuditTestA", "CUST");

    @ParameterizedTest
    @EnumSource(ConfirmationStore.Attempt.class)
    void everyAttemptOutcomeIsAnAuditCode(ConfirmationStore.Attempt outcome) {
        assertThatCode(() -> new AuditEvent(AuditEvent.Action.QUOTE_CONFIRMATION, AuditEvent.Resource.QUOTE,
                outcome.name(), CUSTOMER)).doesNotThrowAnyException();
    }
}
