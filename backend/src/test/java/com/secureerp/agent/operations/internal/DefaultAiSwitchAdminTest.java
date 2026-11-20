package com.secureerp.agent.operations.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.audit.AuditEvent;
import com.secureerp.agent.audit.RecordingAuditTrail;
import com.secureerp.agent.identity.IdentityFixtures;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.platform.KillSwitch;
import com.secureerp.agent.platform.TestKillSwitch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;

/** RBAC_MATRIX "Bật/tắt kill switch": OPS only in the service as well (the URL rule is defence in depth). */
class DefaultAiSwitchAdminTest {

    private final TestKillSwitch killSwitch = new TestKillSwitch();
    private final RecordingAuditTrail audit = new RecordingAuditTrail();
    private final DefaultAiSwitchAdmin admin = new DefaultAiSwitchAdmin(killSwitch, audit);

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "OPS", mode = EnumSource.Mode.EXCLUDE)
    void anyoneButAnOperatorIsRefused(Role role) {
        SessionIdentity other = role == Role.CUST ? IdentityFixtures.sessionIdentity("NotOps", "NotOpsCustomer")
                : IdentityFixtures.staffIdentity("NotOps" + role, role);

        assertThatThrownBy(() -> admin.set(other, false)).isInstanceOfSatisfying(ApplicationException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> admin.state(other)).isInstanceOf(ApplicationException.class);
        assertThat(killSwitch.isAiEnabled()).isTrue();
        // EP-09b: each denial is an audit event with the role, in its own transaction (decision 2A).
        assertThat(audit.separate()).hasSize(2).allSatisfy(event -> {
            assertThat(event.action()).isEqualTo(AuditEvent.Action.ACCESS_DENIED);
            assertThat(event.resource()).isEqualTo(AuditEvent.Resource.KILL_SWITCH);
            assertThat(event.outcome()).isEqualTo(AuditEvent.FORBIDDEN);
            assertThat(event.actor().role()).isEqualTo(role.name());
        });
        assertThat(audit.joined()).isEmpty();
    }

    /**
     * A-44(d): a change that cannot be stored answers 503; the operator must not believe it took effect. Also
     * when no connection or transaction can be had (review round 2, security L-3, backend L1).
     */
    @ParameterizedTest
    @ValueSource(strings = {"data", "connection", "commit"})
    void aChangeThatCannotBeStoredIsAServiceError(String failure) {
        RuntimeException thrown = switch (failure) {
            case "data" -> new DataAccessResourceFailureException("down");
            case "connection" -> new CannotCreateTransactionException("down");
            default -> new TransactionSystemException("commit failed");
        };
        KillSwitch broken = new TestKillSwitch() {
            @Override
            public State set(boolean enabled, String actorPseudonym) {
                throw thrown;
            }
        };
        SessionIdentity operator = IdentityFixtures.staffIdentity("Operator", Role.OPS);

        assertThatThrownBy(() -> new DefaultAiSwitchAdmin(broken, audit).set(operator, false))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
    }

    @Test
    void anOperatorSwitchesTheAiOffAndOn() {
        SessionIdentity operator = IdentityFixtures.staffIdentity("Operator", Role.OPS);

        assertThat(admin.set(operator, false).disabledReason()).isEqualTo(KillSwitch.Reason.OPERATOR);
        assertThat(admin.state(operator).aiEnabled()).isFalse();
        assertThat(admin.set(operator, true).aiEnabled()).isTrue();
    }
}
