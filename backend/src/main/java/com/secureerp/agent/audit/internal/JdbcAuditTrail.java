package com.secureerp.agent.audit.internal;

import com.secureerp.agent.audit.AuditActor;
import com.secureerp.agent.audit.AuditEvent;
import com.secureerp.agent.audit.AuditTrail;
import com.secureerp.agent.platform.TraceIds;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes {@code audit_event} (Flyway V14). {@link #record} joins the caller's transaction ({@code MANDATORY}),
 * {@link #recordSeparately} uses its own ({@code REQUIRES_NEW}; a second pooled connection while the
 * caller holds one, as {@code EgressRecordWriter}). Logs carry the exception class only.
 */
@Component
class JdbcAuditTrail implements AuditTrail {

    private static final Logger log = LoggerFactory.getLogger(JdbcAuditTrail.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate joined;
    private final TransactionTemplate own;
    private final Clock clock;

    JdbcAuditTrail(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.joined = new TransactionTemplate(transactionManager);
        // EP-09b reviews (architecture M2): a change and its record must share a transaction; a caller without
        // one is a programming error and fails at once instead of writing the record on its own.
        this.joined.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);
        this.own = new TransactionTemplate(transactionManager);
        this.own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Override
    public void record(AuditEvent event) {
        Objects.requireNonNull(event, "event");
        joined.executeWithoutResult(status -> insert(event));
    }

    @Override
    public void recordSeparately(AuditEvent event) {
        try {
            Objects.requireNonNull(event, "event");
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                // EP-09b reviews M-1: a second connection while the caller holds one; callers record denials
                // after their transaction (DeniedInTransaction). Written anyway, but tests look for this line.
                log.error("audit.event separate_write_inside_transaction");
            }
            own.executeWithoutResult(status -> insert(event));
        } catch (RuntimeException failure) {
            log.error("audit.event not_recorded exception={}", failure.getClass().getSimpleName());
        }
    }

    private void insert(AuditEvent event) {
        AuditActor actor = event.actor();
        jdbc.update("insert into audit_event (trace_id, action, resource_type, outcome, actor, actor_role, occurred_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                TraceIds.current().orElse(null), event.action().name(), event.resource().name(), event.outcome(),
                actor == null ? null : actor.pseudonym(), actor == null ? null : actor.role(),
                Timestamp.from(clock.instant()));
    }
}
