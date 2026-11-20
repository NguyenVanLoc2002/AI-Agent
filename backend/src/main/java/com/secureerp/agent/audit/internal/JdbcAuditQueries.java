package com.secureerp.agent.audit.internal;

import com.secureerp.agent.audit.AuditEvent;
import com.secureerp.agent.audit.queries.AuditQueries;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads {@code egress_record} and {@code audit_event}; both tables hold metadata only (ADR-012). */
@Component
class JdbcAuditQueries implements AuditQueries {

    private static final String EVENT_COLUMNS =
            "action, resource_type, outcome, actor, actor_role, trace_id, occurred_at";

    private final JdbcTemplate jdbc;

    JdbcAuditQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EgressRow> egressOfTrace(String traceId, int limit) {
        return jdbc.query("select decision, attempt, policy_code, honeytoken_id, blocked_field_path, field_names, "
                        + "byte_length, occurred_at from egress_record where trace_id = ? or request_id = ? order by id limit ?",
                (rs, row) -> new EgressRow(rs.getString("decision"), rs.getInt("attempt"), rs.getString("policy_code"),
                        rs.getString("honeytoken_id"), rs.getString("blocked_field_path"),
                        names(rs.getString("field_names")), rs.getInt("byte_length"),
                        rs.getTimestamp("occurred_at").toInstant()),
                traceId, traceId, limit);
    }

    @Override
    public List<EventRow> eventsOfTrace(String traceId, int limit) {
        return jdbc.query("select " + EVENT_COLUMNS + " from audit_event where trace_id = ? order by id limit ?",
                (rs, row) -> event(rs), traceId, limit);
    }

    @Override
    public List<EventRow> events(AuditEvent.Action action, Instant from, Instant to, int limit) {
        if (action == null) {
            return jdbc.query("select " + EVENT_COLUMNS + " from audit_event where occurred_at >= ? and occurred_at <= ? "
                    + "order by occurred_at desc, id desc limit ?", (rs, row) -> event(rs),
                    Timestamp.from(from), Timestamp.from(to), limit);
        }
        return jdbc.query("select " + EVENT_COLUMNS + " from audit_event where action = ? and occurred_at >= ? "
                + "and occurred_at <= ? order by occurred_at desc, id desc limit ?", (rs, row) -> event(rs),
                action.name(), Timestamp.from(from), Timestamp.from(to), limit);
    }

    private static EventRow event(ResultSet rs) throws SQLException {
        return new EventRow(AuditEvent.Action.valueOf(rs.getString("action")),
                AuditEvent.Resource.valueOf(rs.getString("resource_type")), rs.getString("outcome"),
                rs.getString("actor"), rs.getString("actor_role"), rs.getString("trace_id"),
                rs.getTimestamp("occurred_at").toInstant());
    }

    private static List<String> names(String joined) {
        return joined == null || joined.isEmpty() ? List.of() : Arrays.asList(joined.split(","));
    }
}
