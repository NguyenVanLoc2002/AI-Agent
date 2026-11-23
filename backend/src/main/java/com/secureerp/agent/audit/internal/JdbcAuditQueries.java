package com.secureerp.agent.audit.internal;

import com.secureerp.agent.audit.AuditEvent;
import com.secureerp.agent.audit.queries.AuditQueries;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads {@code egress_record} and {@code audit_event}; both tables hold metadata only (ADR-012). */
@Component
class JdbcAuditQueries implements AuditQueries {

    private static final String EVENT_COLUMNS = "id, action, resource_type, outcome, actor, actor_role, trace_id, "
            + "occurred_at, occurrences, last_occurred_at, last_trace_id";

    private final JdbcTemplate jdbc;

    JdbcAuditQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EgressRow> egressOfTrace(String traceId, int limit) {
        return jdbc.query("select decision, attempt, policy_code, honeytoken_id, blocked_field_path, field_names, "
                        + "byte_length, occurred_at, direction from egress_record where trace_id = ? or request_id = ? "
                        + "order by id limit ?",
                (rs, row) -> new EgressRow(rs.getString("decision"), rs.getInt("attempt"), rs.getString("policy_code"),
                        rs.getString("honeytoken_id"), rs.getString("blocked_field_path"),
                        names(rs.getString("field_names")), rs.getInt("byte_length"),
                        rs.getTimestamp("occurred_at").toInstant(), rs.getString("direction")),
                traceId, traceId, limit);
    }

    @Override
    public List<EventRow> eventsOfTrace(String traceId, int limit) {
        // The first or the latest trace of a collapsed row (A-45(z)); the ones in between are counted, not traced.
        return jdbc.query("select " + EVENT_COLUMNS + " from audit_event where trace_id = ? or last_trace_id = ? "
                + "order by id limit ?", (rs, row) -> event(rs), traceId, traceId, limit);
    }

    @Override
    public List<EventRow> events(AuditEvent.Action action, String actorRole, Instant from, Instant to, Before before,
            int limit) {
        // A collapsed row is in the window when any of its events is (EP-09b reviews L3): from its last to its first.
        StringBuilder sql = new StringBuilder("select " + EVENT_COLUMNS
                + " from audit_event where coalesce(last_occurred_at, occurred_at) >= ? and occurred_at <= ?");
        List<Object> arguments = new ArrayList<>(List.of(Timestamp.from(from), Timestamp.from(to)));
        if (action != null) {
            sql.append(" and action = ?");
            arguments.add(action.name());
        }
        if (actorRole != null) {
            sql.append(" and actor_role = ?");
            arguments.add(actorRole);
        }
        if (before != null) {
            sql.append(" and (occurred_at < ? or (occurred_at = ? and id < ?))");
            arguments.add(Timestamp.from(before.at()));
            arguments.add(Timestamp.from(before.at()));
            arguments.add(before.id());
        }
        sql.append(" order by occurred_at desc, id desc limit ?");
        arguments.add(limit);
        return jdbc.query(sql.toString(), (rs, row) -> event(rs), arguments.toArray());
    }

    private static EventRow event(ResultSet rs) throws SQLException {
        Timestamp last = rs.getTimestamp("last_occurred_at");
        return new EventRow(rs.getLong("id"), AuditEvent.Action.valueOf(rs.getString("action")),
                AuditEvent.Resource.valueOf(rs.getString("resource_type")), rs.getString("outcome"),
                rs.getString("actor"), rs.getString("actor_role"), rs.getString("trace_id"),
                rs.getTimestamp("occurred_at").toInstant(), rs.getInt("occurrences"),
                last == null ? null : last.toInstant(), rs.getString("last_trace_id"));
    }

    private static List<String> names(String joined) {
        return joined == null || joined.isEmpty() ? List.of() : Arrays.asList(joined.split(","));
    }
}
