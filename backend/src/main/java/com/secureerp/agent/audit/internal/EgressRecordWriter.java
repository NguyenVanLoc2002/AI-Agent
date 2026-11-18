package com.secureerp.agent.audit.internal;

import com.secureerp.agent.audit.EgressDecisionEvent;
import java.sql.Timestamp;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persists {@link EgressDecisionEvent}s into {@code egress_record} (V2 migration).
 *
 * <p>Invariants the gateway's "no egress without an audit record" guarantee depends on
 * (AI_GATEWAY §6a). Do not change them without changing the gateway:
 * <ul>
 *   <li><b>synchronous</b> plain {@code @EventListener}: no {@code @Async}, no
 *       {@code @ApplicationModuleListener}, no {@code @TransactionalEventListener};</li>
 *   <li><b>own transaction</b> ({@code REQUIRES_NEW}): a later rollback of the caller's transaction
 *       cannot erase the record of bytes that were already sent;</li>
 *   <li><b>fail-propagating</b>: an insert failure throws to the publisher, so the gateway sends
 *       nothing.</li>
 * </ul>
 * {@code GatewayEgressIT} proves the last two against PostgreSQL.
 */
@Component
class EgressRecordWriter {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate ownTransaction;

    EgressRecordWriter(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @EventListener
    void on(EgressDecisionEvent event) {
        ownTransaction.executeWithoutResult(status -> jdbc.update("""
                insert into egress_record
                    (trace_id, decision, policy_code, honeytoken_id, blocked_field_path, field_names, byte_length, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                event.traceId(), event.decision().name(), event.policyCode(), event.honeytokenId(),
                event.blockedFieldPath(), String.join(",", event.fieldNames()), event.byteLength(),
                Timestamp.from(event.occurredAt())));
    }
}
