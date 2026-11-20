package com.secureerp.agent.platform.internal;

import com.secureerp.agent.platform.KillSwitch;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code feature_switch} / {@code feature_switch_event} (Flyway V13). Reads are cached for at most
 * {@link #MAX_TTL}; every failure to read is the disabled state {@link KillSwitch.Reason#UNREADABLE} and
 * is not cached, so the switch recovers as soon as the database does. Writes run in their own transaction
 * under a row lock: a provider-auth trip must stick even when the caller's work is rolled back.
 *
 * <p>EP-09a reviews (M1 of all three): the cache holds the row version and is only replaced by a state at
 * least as new, so a read that started before a write on this instance cannot put the older state back.
 * The breaker is decided under the row lock from the stored state, not from the cache (security M2).
 * Logs carry the state only.
 */
@Service
class JdbcKillSwitch implements KillSwitch {

    private static final Logger log = LoggerFactory.getLogger(JdbcKillSwitch.class);
    static final String NAME = "ai_assistant";
    /** ADR-013: a change reaches every instance within 30 seconds. */
    static final Duration MAX_TTL = Duration.ofSeconds(30);
    private static final Pattern ACTOR = Pattern.compile("^usr_[A-Za-z0-9_-]{22}$");

    /**
     * A stored state, its row version, and when it was read or written here. {@code mustReread}: a read
     * failed since, so the entry is not served, but its version still guards against a stale read (review
     * round 2, security L-1).
     */
    record Cached(State state, long version, Instant readAt, boolean mustReread) {

        Cached(State state, long version, Instant readAt) {
            this(state, version, readAt, false);
        }
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate ownTransaction;
    private final Clock clock;
    private final Duration ttl;
    private final AtomicReference<Cached> cached = new AtomicReference<>();

    JdbcKillSwitch(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock,
            @Value("${apas.kill-switch.cache-ttl:PT30S}") Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException("apas.kill-switch.cache-ttl must be between 0 and 30 seconds");
        }
        this.jdbc = jdbc;
        this.clock = clock;
        this.ttl = ttl;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public State current() {
        Instant now = clock.instant();
        Cached snapshot = cached.get();
        if (snapshot != null && !snapshot.mustReread() && fresh(snapshot, now)) {
            return snapshot.state();
        }
        try {
            Cached read = read(now);
            Cached kept = remember(read);
            // A state older than what this instance wrote within the TTL is not served (stale read).
            return kept.state();
        } catch (RuntimeException unreadable) {
            readFailed(unreadable);
            return State.unreadable();
        }
    }

    /**
     * Fail closed (ADR-013): the next call reads again, while the cached version keeps guarding against a
     * read that started before the last write (review round 2, security L-1). Package-private for the test
     * of a read that fails on another thread.
     */
    void readFailed(RuntimeException unreadable) {
        cached.updateAndGet(old -> old == null ? null : new Cached(old.state(), old.version(), old.readAt(), true));
        log.error("platform.kill_switch unreadable exception={}", unreadable.getClass().getSimpleName());
    }

    @Override
    public State set(boolean enabled, String actorPseudonym) {
        Objects.requireNonNull(actorPseudonym, "actorPseudonym");
        if (!ACTOR.matcher(actorPseudonym).matches()) {
            throw new IllegalArgumentException("actor must be a pseudonymous principal");
        }
        return write(enabled ? null : Reason.OPERATOR, actorPseudonym, false);
    }

    @Override
    public void tripProviderAuth() {
        try {
            write(Reason.PROVIDER_AUTH, null, true);
        } catch (RuntimeException failure) {
            // The provider refused our key and the breaker could not be stored: every gateway call still
            // gets 401/403 and trips again, so nothing reaches the model meanwhile.
            log.error("platform.kill_switch trip_failed exception={}", failure.getClass().getSimpleName());
        }
    }

    private boolean fresh(Cached entry, Instant now) {
        return !now.isBefore(entry.readAt()) && now.isBefore(entry.readAt().plus(ttl));
    }

    /**
     * Keeps the newer of the cached and the given state by row version; a state of the same version
     * refreshes the read time. A lower version is accepted only from a read that started after the cached
     * entry was written or read: that is the database lowered out of band (re-seed, restore), which is the
     * source of truth (review round 2, security L-2, architecture L-5); a read that started before a write
     * on this instance is always refused (round 1, M1). {@code readAt} of a read is when it started.
     * Package-private for the stale-read tests.
     */
    Cached remember(Cached candidate) {
        return cached.accumulateAndGet(candidate, (old, offered) -> old == null
                || offered.version() >= old.version() || offered.readAt().isAfter(old.readAt()) ? offered : old);
    }

    /**
     * @param reason {@code null} = enabled
     * @param onlyIfNotAlready a breaker trip: no change (and no event) when the stored reason already is it
     */
    private State write(Reason reason, String actor, boolean onlyIfNotAlready) {
        Instant now = clock.instant();
        Cached written = Objects.requireNonNull(ownTransaction.execute(tx -> {
            List<Cached> stored = jdbc.query("select enabled, reason, updated_at, row_version from feature_switch "
                    + "where name = ? for update", (rs, row) -> toCached(rs.getBoolean("enabled"),
                    rs.getString("reason"), rs.getTimestamp("updated_at").toInstant(), rs.getLong("row_version"),
                    now), NAME);
            if (stored.isEmpty()) {
                throw new IllegalStateException("feature switch row missing");
            }
            if (onlyIfNotAlready && stored.getFirst().state().disabledReason() == reason) {
                return stored.getFirst();
            }
            Long version = jdbc.queryForObject("update feature_switch set enabled = ?, reason = ?, updated_at = ?, "
                    + "row_version = row_version + 1 where name = ? returning row_version", Long.class,
                    reason == null, reason == null ? null : reason.name(), Timestamp.from(now), NAME);
            jdbc.update("insert into feature_switch_event (name, enabled, reason, actor, occurred_at) "
                    + "values (?, ?, ?, ?, ?)", NAME, reason == null, reason == null ? null : reason.name(), actor,
                    Timestamp.from(now));
            log.warn("platform.kill_switch changed ai_enabled={} reason={} by={}", reason == null, reason,
                    actor == null ? "SYSTEM" : "OPERATOR");
            return new Cached(new State(reason == null, reason, now), Objects.requireNonNull(version), now);
        }));
        // The state just committed is the answer, and the cache takes it even after an out-of-band version
        // regression (review round 2, security L-2).
        cached.set(written);
        return written.state();
    }

    private Cached read(Instant now) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "select enabled, reason, updated_at, row_version from feature_switch where name = ?",
                (rs, row) -> toCached(rs.getBoolean("enabled"), rs.getString("reason"),
                        rs.getTimestamp("updated_at").toInstant(), rs.getLong("row_version"), now), NAME));
    }

    /** An unknown stored reason throws, and the caller treats that as unreadable. */
    private static Cached toCached(boolean enabled, String reason, Instant since, long version, Instant readAt) {
        Reason parsed = enabled ? null : Reason.valueOf(Objects.requireNonNull(reason, "reason"));
        if (parsed == Reason.UNREADABLE) {
            throw new IllegalStateException("UNREADABLE is never stored");
        }
        return new Cached(new State(enabled, parsed, since), version, readAt);
    }
}
