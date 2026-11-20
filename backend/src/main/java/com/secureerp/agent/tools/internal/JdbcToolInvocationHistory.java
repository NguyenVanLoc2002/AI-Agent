package com.secureerp.agent.tools.internal;

import com.secureerp.agent.tools.history.ToolInvocationHistory;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads {@code tool_invocation} without its principal and conversation columns. */
@Repository
class JdbcToolInvocationHistory implements ToolInvocationHistory {

    private final JdbcTemplate jdbc;

    JdbcToolInvocationHistory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Invocation> ofTurn(String turnId, int limit) {
        // The turn ID, or the request ID of the turn (A-45(h)): one trace ID finds the calls either way.
        return jdbc.query("select tool_name, decision, reason_code, occurred_at from tool_invocation "
                        + "where turn_id = ? or request_id = ? order by id limit ?",
                (rs, row) -> new Invocation(rs.getString("tool_name"), rs.getString("decision"),
                        rs.getString("reason_code"), rs.getTimestamp("occurred_at").toInstant()),
                turnId, turnId, limit);
    }
}
