package com.secureerp.agent.conversation.internal;

import com.secureerp.agent.identity.PrincipalId;
import com.secureerp.agent.platform.ConversationId;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code conversation} and {@code message} (Flyway V9). Every read of a conversation filters by the
 * owning principal (rule 20: ownership on every row access); there is no lookup by id alone. Texts are
 * stored only after sanitizing (customer) or validation (bot); they are never logged.
 *
 * <p>Admission of a customer message and creation of a conversation are atomic (EP-04 reviews: security
 * M1, architecture M1/M3/M4): a short transaction holds an advisory lock per conversation (or per
 * principal for creation), so dedup, the one-turn-in-flight rule, the status read and the limits cannot
 * race. The model call itself runs outside any transaction.
 */
@Component
class ConversationStore {

    /** Advisory lock namespaces (tools 30_003, quote 30_006). */
    static final int TURN_LOCK = 30_010;
    static final int CREATE_LOCK = 30_011;

    enum Status {
        ACTIVE_BOT,
        HANDOFF_PENDING
    }

    enum Direction {
        CUSTOMER,
        BOT
    }

    record Conversation(ConversationId id, String customerDigest, Status status) {
    }

    /** A previous turn found by its client event ID; {@code botText} is empty while the turn runs. */
    record PreviousTurn(UUID turnId, Instant startedAt, Optional<String> botText, boolean handedOff) {
    }

    record StoredMessage(Direction direction, String text) {
    }

    /** Limits checked at admission (ConversationProperties). */
    record TurnLimits(int perConversation, int perPrincipal, Instant since, Instant inFlightSince) {
    }

    /** Outcome of admitting one customer message. */
    sealed interface Admission {

        /** Stored; {@code status} is the conversation status read under the lock. */
        record Admitted(long messageId, Status status) implements Admission {
        }

        record Duplicate(PreviousTurn turn) implements Admission {
        }

        /** Another turn of this conversation is still running. */
        record InProgress() implements Admission {
        }

        record RateLimited() implements Admission {
        }
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    ConversationStore(JdbcTemplate jdbc, TransactionTemplate transactions, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Creates a conversation unless the principal reached the hourly limit (atomic per principal). */
    Optional<ConversationId> create(PrincipalId owner, String customerDigest, String channel, int limit, Instant since) {
        return transactions.execute(tx -> {
            jdbc.queryForList("select pg_advisory_xact_lock(?, hashtext(?))", CREATE_LOCK, owner.pseudonym());
            Integer count = jdbc.queryForObject(
                    "select count(*) from conversation where owner_principal = ? and created_at > ?", Integer.class,
                    owner.pseudonym(), Timestamp.from(since));
            if (count != null && count >= limit) {
                return Optional.<ConversationId>empty();
            }
            byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            ConversationId id = new ConversationId("cv_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
            Timestamp now = Timestamp.from(clock.instant());
            jdbc.update("insert into conversation (conversation_id, owner_principal, customer_digest, channel, status, "
                            + "created_at, updated_at) values (?, ?, ?, ?, 'ACTIVE_BOT', ?, ?)",
                    id.value(), owner.pseudonym(), customerDigest, channel, now, now);
            return Optional.of(id);
        });
    }

    Optional<Conversation> findOwned(PrincipalId owner, ConversationId id) {
        return jdbc.query("select conversation_id, customer_digest, status from conversation "
                                + "where conversation_id = ? and owner_principal = ?",
                        (rs, row) -> new Conversation(new ConversationId(rs.getString(1)), rs.getString(2),
                                Status.valueOf(rs.getString(3))),
                        id.value(), owner.pseudonym())
                .stream().findFirst();
    }

    /**
     * Admits one customer message: dedup by client event, at most one turn in flight per conversation
     * (an unanswered message newer than {@code inFlightSince}), limits per conversation and per principal,
     * then insert. The conversation status is read under the same lock.
     */
    Admission admit(PrincipalId owner, ConversationId id, UUID turnId, UUID clientEventId, String text,
            boolean blocked, TurnLimits limits) {
        return Objects.requireNonNull(transactions.execute(tx -> {
            jdbc.queryForList("select pg_advisory_xact_lock(?, hashtext(?))", TURN_LOCK, id.value());
            Optional<PreviousTurn> previous = findTurn(id, clientEventId);
            if (previous.isPresent()) {
                return new Admission.Duplicate(previous.get());
            }
            Integer inFlight = jdbc.queryForObject("select count(*) from message c where c.conversation_id = ? "
                    + "and c.direction = 'CUSTOMER' and c.created_at > ? and not exists (select 1 from message b "
                    + "where b.turn_id = c.turn_id and b.direction = 'BOT')", Integer.class, id.value(),
                    Timestamp.from(limits.inFlightSince()));
            if (inFlight != null && inFlight > 0) {
                return new Admission.InProgress();
            }
            Integer inConversation = jdbc.queryForObject("select count(*) from message where conversation_id = ? "
                    + "and direction = 'CUSTOMER' and created_at > ?", Integer.class, id.value(),
                    Timestamp.from(limits.since()));
            Integer byPrincipal = jdbc.queryForObject("select count(*) from message m join conversation c "
                    + "on c.conversation_id = m.conversation_id where c.owner_principal = ? "
                    + "and m.direction = 'CUSTOMER' and m.created_at > ?", Integer.class, owner.pseudonym(),
                    Timestamp.from(limits.since()));
            if ((inConversation != null && inConversation >= limits.perConversation())
                    || (byPrincipal != null && byPrincipal >= limits.perPrincipal())) {
                return new Admission.RateLimited();
            }
            Status status = jdbc.queryForObject("select status from conversation where conversation_id = ? "
                    + "and owner_principal = ?", (rs, row) -> Status.valueOf(rs.getString(1)), id.value(),
                    owner.pseudonym());
            Long messageId = jdbc.queryForObject("insert into message (conversation_id, turn_id, direction, "
                            + "client_event_id, text, blocked, created_at) values (?, ?, 'CUSTOMER', ?, ?, ?, ?) "
                            + "returning message_id", Long.class,
                    id.value(), turnId, clientEventId, text, blocked, Timestamp.from(clock.instant()));
            return new Admission.Admitted(Objects.requireNonNull(messageId), status);
        }));
    }

    Optional<PreviousTurn> findTurn(ConversationId id, UUID clientEventId) {
        return jdbc.query("select c.turn_id, c.created_at, b.text, b.handed_off from message c left join message b "
                                + "on b.turn_id = c.turn_id and b.direction = 'BOT' "
                                + "where c.conversation_id = ? and c.client_event_id = ?",
                        (rs, row) -> new PreviousTurn(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(),
                                Optional.ofNullable(rs.getString(3)), rs.getBoolean(4)),
                        id.value(), clientEventId)
                .stream().findFirst();
    }

    /**
     * Locks the bot (EP-04 architecture review M3): the status change and the handoff record are written
     * by the caller in one transaction; idempotent, guarded by the owner.
     */
    void markHandoffPending(PrincipalId owner, ConversationId id) {
        jdbc.update("update conversation set status = 'HANDOFF_PENDING', row_version = row_version + 1, updated_at = ? "
                        + "where conversation_id = ? and owner_principal = ? and status = 'ACTIVE_BOT'",
                Timestamp.from(clock.instant()), id.value(), owner.pseudonym());
    }

    /**
     * Locks the bot for a server-side trigger that has no session principal (EP-07: the confirmation of
     * a quote needs staff). Guarded by the customer the conversation is bound to (A-35), which the caller
     * takes from the quote row, never from a request. Idempotent.
     */
    void markHandoffPendingForCustomer(ConversationId id, String customerDigest) {
        jdbc.update("update conversation set status = 'HANDOFF_PENDING', row_version = row_version + 1, updated_at = ? "
                        + "where conversation_id = ? and customer_digest = ? and status = 'ACTIVE_BOT'",
                Timestamp.from(clock.instant()), id.value(), customerDigest);
    }

    /** Stores the turn's reply once (unique (turn, direction)); a second write for the same turn is ignored. */
    void insertBot(ConversationId id, UUID turnId, String text, boolean handedOff) {
        jdbc.update("insert into message (conversation_id, turn_id, direction, text, handed_off, created_at) "
                        + "values (?, ?, 'BOT', ?, ?, ?) on conflict (turn_id, direction) do nothing",
                id.value(), turnId, text, handedOff, Timestamp.from(clock.instant()));
    }

    /**
     * ADR-011: the last {@code limit} sanitized, not blocked customer texts up to and including this turn's
     * message, oldest first. Bounded by the turn's message ID so a concurrent turn can never become this
     * turn's "current" message (architecture review M1).
     */
    List<String> customerTextsUpTo(ConversationId id, long messageId, int limit) {
        List<String> newestFirst = jdbc.queryForList("select text from message where conversation_id = ? "
                + "and direction = 'CUSTOMER' and not blocked and message_id <= ? order by message_id desc limit ?",
                String.class, id.value(), messageId, limit);
        return newestFirst.reversed();
    }

    /** Customer and bot messages for the owner's view, oldest first (staff messages are EP-08). */
    List<StoredMessage> messages(ConversationId id, int limit) {
        List<StoredMessage> newestFirst = jdbc.query("select direction, text from message where conversation_id = ? "
                        + "and direction in ('CUSTOMER', 'BOT') order by message_id desc limit ?",
                (rs, row) -> new StoredMessage(Direction.valueOf(rs.getString(1)), rs.getString(2)), id.value(), limit);
        return newestFirst.reversed();
    }
}
