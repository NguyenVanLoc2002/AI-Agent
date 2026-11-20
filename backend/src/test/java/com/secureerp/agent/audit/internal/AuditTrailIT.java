package com.secureerp.agent.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.TestcontainersConfiguration;
import com.secureerp.agent.audit.AuditActor;
import com.secureerp.agent.audit.AuditEvent;
import com.secureerp.agent.audit.AuditTrail;
import com.secureerp.agent.audit.queries.AuditQueries;
import com.secureerp.agent.platform.MutableClock;
import com.secureerp.agent.platform.TraceIds;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** EP-09b (decision 2A) against PostgreSQL: the two write modes, the trace ID and the V14 constraints. */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@Import({TestcontainersConfiguration.class, AuditTrailIT.FixedClock.class})
class AuditTrailIT {

    private static final Instant START = Instant.parse("2026-11-20T08:00:10Z");

    /** AUDIT-FLOOD reviews M2: a test clock, so collapsing never depends on where the wall clock is in its minute. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        @Primary
        MutableClock auditTestClock() {
            return new MutableClock(START);
        }
    }

    @Autowired
    MutableClock clock;

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
        clock.set(START);
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
        Instant now = clock.instant();
        assertThat(queries.events(null, null, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), null, 10))
                .extracting(AuditQueries.EventRow::action)
                .containsExactly(AuditEvent.Action.ACCESS_DENIED, AuditEvent.Action.HANDOFF_CLAIMED);
        assertThat(queries.events(AuditEvent.Action.ACCESS_DENIED, null, now.minus(1, ChronoUnit.HOURS),
                now.plus(1, ChronoUnit.HOURS), null, 10)).hasSize(1);
        assertThat(queries.events(null, null, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), null, 1)).hasSize(1);
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
        // AUDIT-FLOOD: the same denial of the same minute is one row counted twice.
        assertThat(jdbc.queryForObject("select occurrences from audit_event", Integer.class)).isEqualTo(2);
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

    /**
     * AUDIT-FLOOD (A-45(z), V16): identical separate events of one actor in one minute are one row with a count,
     * also when written at once; another actor, outcome or minute is another row; a write that commits with a
     * business change is never collapsed.
     */
    @Test
    void identicalSeparateEventsOfAMinuteAreCollapsed() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> writes = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 40; i++) {
                writes.add(pool.submit(() -> {
                    start.await();
                    audit.recordSeparately(DENIED);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> write : writes) {
                write.get(30, TimeUnit.SECONDS);
            }
        }
        audit.recordSeparately(AuditEvent.denied(AuditEvent.Resource.HANDOFF, AuditEvent.FORBIDDEN_RESOURCE,
                new AuditActor("usr_AuditTrailTestOtherAAA", "SALES")));
        audit.recordSeparately(AuditEvent.denied(AuditEvent.Resource.HANDOFF, AuditEvent.FORBIDDEN, ACTOR));
        transactions.executeWithoutResult(tx -> {
            audit.record(CLAIMED);
            audit.record(CLAIMED);
        });

        assertThat(jdbc.queryForList("select outcome || ':' || occurrences from audit_event "
                + "where action = 'ACCESS_DENIED' order by id", String.class))
                .containsExactly("FORBIDDEN_RESOURCE:40", "FORBIDDEN_RESOURCE:1", "FORBIDDEN:1");
        assertThat(jdbc.queryForList("select occurrences from audit_event where action = 'HANDOFF_CLAIMED'",
                Integer.class)).containsExactly(1, 1);
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'HANDOFF_CLAIMED' "
                + "and bucket_start is null", Integer.class)).as("never collapsed").isEqualTo(2);

        // The next minute is not joined: the next identical event starts a new row.
        clock.advance(Duration.ofMinutes(1));
        audit.recordSeparately(AuditEvent.denied(AuditEvent.Resource.HANDOFF, AuditEvent.FORBIDDEN, ACTOR));
        assertThat(jdbc.queryForObject("select count(*) from audit_event where outcome = 'FORBIDDEN'", Integer.class))
                .isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("update audit_event set occurrences = 2 where bucket_start is null"))
                .as("a row that never collapses counts one").isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * AUDIT-FLOOD reviews M1/L2: an event in the last half microsecond of a minute is stored in that minute (the
     * instant is cut to microseconds before the bucket is derived), so the V16 check holds and nothing is lost.
     */
    @Test
    void anEventAtTheEndOfAMinuteStaysInItsMinute() {
        clock.set(Instant.parse("2026-11-20T08:00:59.999999700Z"));
        audit.recordSeparately(DENIED);
        clock.set(Instant.parse("2026-11-20T08:00:59.999999900Z"));
        audit.recordSeparately(DENIED);

        assertThat(jdbc.queryForObject("select occurrences from audit_event", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from audit_event "
                + "where bucket_start = date_trunc('minute', occurred_at, 'UTC')", Integer.class)).isEqualTo(1);
    }

    /**
     * AUDIT-FLOOD reviews L1: the latest identical event of a minute is found by its own trace ID; an accepted
     * confirmation is never collapsed (one row per order).
     */
    @Test
    void theLatestCollapsedEventIsTraceableAndAnAcceptedConfirmationIsNeverCollapsed() {
        String first = UUID.randomUUID().toString();
        String latest = UUID.randomUUID().toString();
        for (String trace : List.of(first, UUID.randomUUID().toString(), latest)) {
            MDC.put(TraceIds.MDC_KEY, trace);
            try {
                audit.recordSeparately(DENIED);
            } finally {
                MDC.remove(TraceIds.MDC_KEY);
            }
        }
        AuditEvent accepted = new AuditEvent(AuditEvent.Action.QUOTE_CONFIRMATION, AuditEvent.Resource.QUOTE,
                "ACCEPTED", ACTOR);
        audit.recordSeparately(accepted);
        audit.recordSeparately(accepted);

        assertThat(queries.eventsOfTrace(first, 10)).singleElement()
                .satisfies(row -> assertThat(row.occurrences()).isEqualTo(3));
        assertThat(queries.eventsOfTrace(latest, 10)).singleElement()
                .satisfies(row -> assertThat(row.lastTraceId()).isEqualTo(latest));
        assertThat(jdbc.queryForList("select occurrences from audit_event where outcome = 'ACCEPTED'", Integer.class))
                .containsExactly(1, 1);
    }

    /** AUDIT-FLOOD reviews L3: a collapsed row is in a window that holds any of its events, not only its first. */
    @Test
    void aCollapsedRowIsInEveryWindowThatHoldsOneOfItsEvents() {
        audit.recordSeparately(DENIED);
        clock.advance(Duration.ofSeconds(30));
        audit.recordSeparately(DENIED);

        assertThat(queries.events(null, null, START.plusSeconds(20), START.plusSeconds(40), null, 10))
                .singleElement().satisfies(row -> assertThat(row.occurrences()).isEqualTo(2));
        assertThat(queries.events(null, "SALES", START.plusSeconds(20), START.plusSeconds(40), null, 10)).hasSize(1);
        assertThat(queries.events(null, "CUST", START.plusSeconds(20), START.plusSeconds(40), null, 10)).isEmpty();
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
