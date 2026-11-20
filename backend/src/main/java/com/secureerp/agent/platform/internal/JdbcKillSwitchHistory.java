package com.secureerp.agent.platform.internal;

import com.secureerp.agent.platform.KillSwitch;
import com.secureerp.agent.platform.history.KillSwitchHistory;
import java.sql.Timestamp;
import java.time.Instant;
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
    public List<Change> changes(Instant from, Instant to, int limit) {
        return jdbc.query("select enabled, reason, actor, occurred_at from feature_switch_event "
                        + "where occurred_at >= ? and occurred_at <= ? order by occurred_at desc, id desc limit ?",
                (rs, row) -> new Change(rs.getBoolean("enabled"),
                        rs.getString("reason") == null ? null : KillSwitch.Reason.valueOf(rs.getString("reason")),
                        rs.getString("actor"), rs.getTimestamp("occurred_at").toInstant()),
                Timestamp.from(from), Timestamp.from(to), limit);
    }
}
