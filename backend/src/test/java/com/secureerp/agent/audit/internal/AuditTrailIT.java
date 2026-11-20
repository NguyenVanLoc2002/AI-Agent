package com.secureerp.agent.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.TestcontainersConfiguration;
import com.secureerp.agent.audit.AuditActor;
import com.secureerp.agent.audit.AuditEvent;
import com.secureerp.agent.audit.AuditTrail;
import com.secureerp.agent.audit.queries.AuditQueries;
import com.secureerp.agent.platform.TraceIds;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** EP-09b (decision 2A) against PostgreSQL: the two write modes, the trace ID and the V14 constraints. */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@Import(TestcontainersConfiguration.class)
class AuditTrailIT {

    private static final AuditActor ACTOR = new AuditActor("usr_AuditTrailTestActorAAA", "SALES");
    private static final AuditEvent CLAIMED =
            new AuditEvent(AuditEvent.Action.HANDOFF_CLAIMED, AuditEvent.Resource.HANDOFF, "CLAIMED", ACTOR);
    private static final AuditEvent DENIED =
            AuditEvent.denied(AuditEvent.Resource.HANDOFF, AuditEvent.FORBIDDEN_RESOURCE, ACTOR);

    @Autowired
    AuditTrail audit;

    @Autowired
    AuditQueries queries;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from audit_event");
        MDC.remove(TraceIds.MDC_KEY);
    }

    private int rows() {
        return jdbc.queryForObject("select count(*) from audit_event", Integer.class);
    }

    @Test
    void aJoinedRecordRollsBackWithTheChangeAndASeparateOneDoesNot() {
        transactions.executeWithoutResult(tx -> {
            audit.record(CLAIMED);
            audit.recordSeparately(DENIED);
            tx.setRollbackOnly();
        });

        assertThat(jdbc.queryForList("select action from audit_event", String.class)).containsExactly("ACCESS_DENIED");
    }

    @Test
    void aJoinedRecordThatCannotBeWrittenFailsTheChangeASeparateOneNever() {
        jdbc.execute("alter table audit_event rename to audit_event_moved");
        try {
            assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> audit.record(CLAIMED)))
                    .isInstanceOf(DataAccessException.class);
            assertThatCode(() -> audit.recordSeparately(DENIED)).doesNotThrowAnyException();
        } finally {
            jdbc.execute("alter table audit_event_moved rename to audit_event");
        }
        assertThat(rows()).isZero();
    }

    @Test
    void theTraceIdComesFromTheRequestAndTheRowHoldsCodesOnly() {
        String trace = UUID.randomUUID().toString();
        MDC.put(TraceIds.MDC_KEY, trace);
        try {
            transactions.executeWithoutResult(tx -> audit.record(CLAIMED));
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }
        MDC.put(TraceIds.MDC_KEY, "not-a-trace");
        try {
            audit.recordSeparately(DENIED);
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }

        List<AuditQueries.EventRow> ofTrace = queries.eventsOfTrace(trace, 10);
        assertThat(ofTrace).singleElement().satisfies(row -> {
            assertThat(row.action()).isEqualTo(AuditEvent.Action.HANDOFF_CLAIMED);
            assertThat(row.outcome()).isEqualTo("CLAIMED");
            assertThat(row.actor()).isEqualTo(ACTOR.pseudonym());
            assertThat(row.actorRole()).isEqualTo("SALES");
        });
        assertThat(jdbc.queryForObject("select count(*) from audit_event where trace_id is null", Integer.class))
                .as("a malformed trace ID is not stored").isEqualTo(1);
        Instant now = Instant.now();
        assertThat(queries.events(null, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), 10))
                .extracting(AuditQueries.EventRow::action)
                .containsExactly(AuditEvent.Action.ACCESS_DENIED, AuditEvent.Action.HANDOFF_CLAIMED);
        assertThat(queries.events(AuditEvent.Action.ACCESS_DENIED, now.minus(1, ChronoUnit.HOURS),
                now.plus(1, ChronoUnit.HOURS), 10)).hasSize(1);
        assertThat(queries.events(null, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), 1)).hasSize(1);
    }

    /** Architecture review M2: a change and its record share a transaction; without one, record refuses. */
    @Test
    void aJoinedRecordNeedsTheChangesTransaction() {
        assertThatThrownBy(() -> audit.record(CLAIMED)).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(rows()).isZero();
    }

    /** Security/architecture review M-1: a separate write inside a transaction is flagged (tests look for it). */
    @Test
    void aSeparateWriteInsideATransactionIsFlagged(CapturedOutput output) {
        audit.recordSeparately(DENIED);
        assertThat(output.getAll()).doesNotContain("separate_write_inside_transaction");

        transactions.executeWithoutResult(tx -> audit.recordSeparately(DENIED));

        assertThat(output.getAll()).contains("audit.event separate_write_inside_transaction");
        assertThat(rows()).isEqualTo(2);
    }

    /** Architecture review L5: every action and resource of the code is accepted by the V14 check lists. */
    @Test
    void everyActionAndResourceFitsTheTable() {
        transactions.executeWithoutResult(tx -> {
            for (AuditEvent.Action action : AuditEvent.Action.values()) {
                audit.record(new AuditEvent(action, AuditEvent.Resource.HANDOFF, "OK", ACTOR));
            }
            for (AuditEvent.Resource resource : AuditEvent.Resource.values()) {
                audit.record(new AuditEvent(AuditEvent.Action.ACCESS_DENIED, resource, "FORBIDDEN", ACTOR));
            }
        });
        assertThat(rows()).isEqualTo(AuditEvent.Action.values().length + AuditEvent.Resource.values().length);
    }

    /** V14: closed lists and formats in the database as well, not only in the record. */
    @Test
    void theTableRefusesFreeText() {
        String insert = "insert into audit_event (trace_id, action, resource_type, outcome, actor, actor_role, "
                + "occurred_at) values (?, ?, ?, ?, ?, ?, now())";
        List<Object[]> refused = List.of(
                new Object[] {null, "NEW_ACTION", "HANDOFF", "OK", null, null},
                new Object[] {null, "ACCESS_DENIED", "CUSTOMER", "FORBIDDEN", null, null},
                new Object[] {null, "ACCESS_DENIED", "HANDOFF", "Báo giá 123", null, null},
                new Object[] {null, "ACCESS_DENIED", "HANDOFF", "FORBIDDEN", "sales-1", "SALES"},
                new Object[] {null, "ACCESS_DENIED", "HANDOFF", "FORBIDDEN", ACTOR.pseudonym(), null},
                new Object[] {null, "ACCESS_DENIED", "HANDOFF", "FORBIDDEN", null, "SALES"},
                new Object[] {null, "ACCESS_DENIED", "HANDOFF", "FORBIDDEN", ACTOR.pseudonym(), "ADMIN"},
                new Object[] {"cust-a", "ACCESS_DENIED", "HANDOFF", "FORBIDDEN", null, null});
        for (Object[] values : refused) {
            assertThatThrownBy(() -> jdbc.update(insert, values)).isInstanceOf(DataIntegrityViolationException.class);
        }
        jdbc.update(insert, null, "ACCESS_DENIED", "HANDOFF", "FORBIDDEN", null, null);
        assertThat(rows()).isEqualTo(1);
    }
}
