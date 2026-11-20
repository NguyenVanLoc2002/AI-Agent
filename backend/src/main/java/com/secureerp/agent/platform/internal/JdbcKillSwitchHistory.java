package com.secureerp.agent.platform.internal;

import com.secureerp.agent.platform.KillSwitch;
import com.secureerp.agent.platform.history.KillSwitchHistory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads {@code feature_switch_event} (Flyway V13). */
@Component
class JdbcKillSwitchHistory implements KillSwitchHistory {

    private final JdbcTemplate jdbc;

    JdbcKillSwitchHistory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Change> changes(Instant from, Instant to, Instant beforeAt, long beforeId, int limit) {
        StringBuilder sql = new StringBuilder("select id, enabled, reason, actor, occurred_at from feature_switch_event "
                + "where occurred_at >= ? and occurred_at <= ?");
        List<Object> arguments = new ArrayList<>(List.of(Timestamp.from(from), Timestamp.from(to)));
        if (beforeAt != null) {
            sql.append(" and (occurred_at < ? or (occurred_at = ? and id < ?))");
            arguments.add(Timestamp.from(beforeAt));
            arguments.add(Timestamp.from(beforeAt));
            arguments.add(beforeId);
        }
        sql.append(" order by occurred_at desc, id desc limit ?");
        arguments.add(limit);
        return jdbc.query(sql.toString(),
                (rs, row) -> new Change(rs.getLong("id"), rs.getBoolean("enabled"),
                        rs.getString("reason") == null ? null : KillSwitch.Reason.valueOf(rs.getString("reason")),
                        rs.getString("actor"), rs.getTimestamp("occurred_at").toInstant()),
                arguments.toArray());
    }
}
