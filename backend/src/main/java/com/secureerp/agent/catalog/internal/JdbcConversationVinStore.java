package com.secureerp.agent.catalog.internal;

import com.secureerp.agent.catalog.spi.Vin;
import com.secureerp.agent.platform.ConversationId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL implementation ({@code conversation_vin}, Flyway V5). A stored value that no longer
 * passes {@link Vin} validation is treated as absent (fail closed).
 */
@Repository
class JdbcConversationVinStore implements ConversationVinStore {

    private final JdbcTemplate jdbc;

    JdbcConversationVinStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void put(ConversationId conversation, Vin vin, Instant registeredAt, Instant expiresAt) {
        jdbc.update("""
                insert into conversation_vin (conversation_id, vin, registered_at, expires_at)
                values (?, ?, ?, ?)
                on conflict (conversation_id) do update
                set vin = excluded.vin, registered_at = excluded.registered_at, expires_at = excluded.expires_at
                """, conversation.value(), vin.value(), Timestamp.from(registeredAt), Timestamp.from(expiresAt));
    }

    @Override
    public void clear(ConversationId conversation) {
        jdbc.update("delete from conversation_vin where conversation_id = ?", conversation.value());
    }

    @Override
    public void purgeExpired(Instant now) {
        jdbc.update("delete from conversation_vin where expires_at <= ?", Timestamp.from(now));
    }

    @Override
    public Optional<Vin> current(ConversationId conversation, Instant now) {
        return jdbc.queryForList("select vin from conversation_vin where conversation_id = ? and expires_at > ?",
                        String.class, conversation.value(), Timestamp.from(now))
                .stream().filter(Vin::isValid).map(Vin::new).findFirst();
    }
}
