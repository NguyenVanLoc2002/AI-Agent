package com.secureerp.agent.handoff.internal;

import com.secureerp.agent.handoff.HandoffReason;
import com.secureerp.agent.handoff.Handoffs;
import com.secureerp.agent.platform.ConversationId;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** {@code handoff} table (Flyway V9). At most one OPEN handoff per conversation (partial unique index). */
@Component
class JdbcHandoffs implements Handoffs {

    private static final Logger log = LoggerFactory.getLogger(JdbcHandoffs.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcHandoffs(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void open(ConversationId conversation, HandoffReason reason) {
        Objects.requireNonNull(conversation, "conversation");
        Objects.requireNonNull(reason, "reason");
        int inserted = jdbc.update("insert into handoff (conversation_id, reason_code, status, opened_at) "
                        + "values (?, ?, 'OPEN', ?) on conflict (conversation_id) where status = 'OPEN' do nothing",
                conversation.value(), reason.name(), Timestamp.from(clock.instant()));
        log.info("handoff.open reason={} new={}", reason, inserted == 1);
    }
}
