package com.secureerp.agent.audit.internal;

import com.secureerp.agent.audit.EgressAuditLog;
import com.secureerp.agent.audit.EgressDecisionEvent;
import com.secureerp.agent.platform.TraceIds;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persists {@link EgressDecisionEvent}s into {@code egress_record} (V2, V3, V14, V19 migrations; V19 adds the
 * direction, A-59).
 *
 * <p>Invariants the gateway's "no egress without an audit record" guarantee depends on
 * (AI_GATEWAY §6a). Do not change them without changing the gateway:
 * <ul>
 *   <li><b>direct and synchronous</b>: the gateway calls {@link EgressAuditLog#record} itself (no
 *       application event, no listener that could be missing, asynchronous or swallowed);</li>
 *   <li><b>own transaction</b> ({@code REQUIRES_NEW}): a later rollback of the caller's transaction
 *       cannot erase the record of bytes that were already sent. It needs a second pooled
 *       connection when the caller holds one, so the gateway should be called outside a
 *       transaction (second review L6; pool exhaustion fails closed as AUDIT_UNAVAILABLE);</li>
 *   <li><b>fail-propagating</b>: an insert failure throws to the gateway, which sends nothing.</li>
 * </ul>
 * {@code GatewayEgressIT} proves the last two against PostgreSQL.
 *
 * <p>EP-09b (A-45(h)): {@code request_id} is the HTTP request's correlation ID ({@code platform.TraceIds}), so the
 * security inspector can group the model calls of one chat turn. It never leaves the application: the
 * per-call {@code trace_id} is the one the provider sees.
 */
@Component
class EgressRecordWriter implements EgressAuditLog {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate ownTransaction;

    EgressRecordWriter(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void record(EgressDecisionEvent event) {
        ownTransaction.executeWithoutResult(status -> jdbc.update("""
                insert into egress_record
                    (trace_id, decision, attempt, policy_code, honeytoken_id, blocked_field_path, field_names,
                     byte_length, occurred_at, request_id, direction)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                event.traceId(), event.decision().name(), event.attempt(), event.policyCode(), event.honeytokenId(),
                event.blockedFieldPath(), String.join(",", event.fieldNames()), event.byteLength(),
                Timestamp.from(event.occurredAt()), TraceIds.current().orElse(null), event.direction().name()));
    }
}
