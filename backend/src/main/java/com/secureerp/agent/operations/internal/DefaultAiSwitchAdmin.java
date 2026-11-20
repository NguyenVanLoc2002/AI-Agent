package com.secureerp.agent.operations.internal;

import com.secureerp.agent.audit.AuditEvent;
import com.secureerp.agent.audit.AuditTrail;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.operations.AiSwitchAdmin;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.platform.KillSwitch;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

/** OPS only (RBAC_MATRIX "Bật/tắt kill switch"); the role is checked here as well as at the URL. */
@Service
class DefaultAiSwitchAdmin implements AiSwitchAdmin {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiSwitchAdmin.class);

    private final KillSwitch killSwitch;
    private final AuditTrail audit;

    DefaultAiSwitchAdmin(KillSwitch killSwitch, AuditTrail audit) {
        this.killSwitch = killSwitch;
        this.audit = audit;
    }

    @Override
    public KillSwitch.State state(SessionIdentity operator) {
        requireOperator(operator);
        return killSwitch.current();
    }

    @Override
    public KillSwitch.State set(SessionIdentity operator, boolean enabled) {
        requireOperator(operator);
        try {
            return killSwitch.set(enabled, operator.principal().pseudonym());
        } catch (DataAccessException | TransactionException | IllegalStateException failure) {
            // TransactionException: no connection, or a commit failure whose outcome is unknown; the operator
            // reads the state again with GET (review round 2, security L-3, backend L1).
            // Not stored (database down, row missing): the operator must know; the switch keeps its previous
            // state, or reads as off. A programming error is not turned into an outage (reviews L5).
            log.error("operations.kill_switch set_failed exception={}", failure.getClass().getSimpleName());
            throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private void requireOperator(SessionIdentity operator) {
        Objects.requireNonNull(operator, "operator");
        if (operator.role() != Role.OPS) {
            log.warn("operations.kill_switch denied code=FORBIDDEN");
            // EP-09b: the denial goes to audit_event; the switch changes stay in feature_switch_event (4A).
            audit.recordSeparately(AuditEvent.denied(AuditEvent.Resource.KILL_SWITCH, AuditEvent.FORBIDDEN,
                    operator.auditActor()));
            throw new ApplicationException(ErrorCode.FORBIDDEN);
        }
    }
}
