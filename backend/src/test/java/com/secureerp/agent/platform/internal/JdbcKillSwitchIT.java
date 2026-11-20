package com.secureerp.agent.platform.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.TestcontainersConfiguration;
import com.secureerp.agent.platform.KillSwitch;
import com.secureerp.agent.platform.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@link JdbcKillSwitch} against PostgreSQL (EP-09a reviews: backend M3, architecture M4). Two instances of
 * the class on one database stand for two application instances; each has its own clock, so the 30 s
 * cache window is shown exactly.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JdbcKillSwitchIT {

    private static final Instant START = Instant.parse("2026-11-20T08:00:00Z");
    private static final String OPERATOR = "usr_KillSwitchOperatorAAAA";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactions;

    private final MutableClock clock = new MutableClock(START);

    private JdbcKillSwitch instance(Duration ttl) {
        return new JdbcKillSwitch(jdbc, transactions, clock, ttl);
    }

    @BeforeEach
    @AfterEach
    void reset() {
        jdbc.update("delete from feature_switch_event");
        jdbc.update("update feature_switch set enabled = true, reason = null where name = 'ai_assistant'");
    }

    private int events(String reason) {
        return jdbc.queryForObject("select count(*) from feature_switch_event where reason is not distinct from ?",
                Integer.class, reason);
    }

    @Test
    void theCacheWindowIsBoundedToThirtySeconds() {
        assertThatThrownBy(() -> instance(Duration.ofSeconds(31))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> instance(Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> instance(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> instance(Duration.ZERO)).doesNotThrowAnyException();
    }

    /** ADR-013: another instance's change arrives within the TTL, not later; the cache serves until then. */
    @Test
    void anotherInstancesChangeIsSeenAtTheEndOfTheWindow() {
        JdbcKillSwitch here = instance(Duration.ofSeconds(30));
        JdbcKillSwitch other = instance(Duration.ofSeconds(30));
        assertThat(here.isAiEnabled()).isTrue();

        other.set(false, OPERATOR);

        clock.advance(Duration.ofSeconds(30).minusNanos(1));
        assertThat(here.isAiEnabled()).as("still cached").isTrue();
        clock.advance(Duration.ofNanos(1));
        assertThat(here.current().disabledReason()).isEqualTo(KillSwitch.Reason.OPERATOR);
        // A clock that goes back does not keep an old entry alive.
        other.set(true, OPERATOR);
        clock.set(START);
        assertThat(here.isAiEnabled()).isTrue();
        // Without a cache every call reads the database.
        JdbcKillSwitch uncached = instance(Duration.ZERO);
        assertThat(uncached.isAiEnabled()).isTrue();
        other.set(false, OPERATOR);
        assertThat(uncached.isAiEnabled()).isFalse();
    }

    /** Reviews M1: a read that started before a write on this instance cannot put the older state back. */
    @Test
    void aStaleReadNeverReplacesANewerWrite() {
        JdbcKillSwitch here = instance(Duration.ofSeconds(30));
        assertThat(here.isAiEnabled()).isTrue();
        long before = jdbc.queryForObject("select row_version from feature_switch", Long.class);

        here.tripProviderAuth();
        // The read that began before the trip finishes now and offers its older state.
        // It started before the write, so its read time is earlier.
        here.remember(new JdbcKillSwitch.Cached(new KillSwitch.State(true, null, START), before,
                START.minusMillis(1)));

        assertThat(here.current().disabledReason()).isEqualTo(KillSwitch.Reason.PROVIDER_AUTH);
    }

    /**
     * Review round 2 (security L-1): read A starts, the operator switches off here, read C fails, then A
     * offers its older state. The failed read keeps the version guard, so A does not win.
     */
    @Test
    void aFailedReadInBetweenDoesNotLetAStaleReadWin() {
        JdbcKillSwitch here = instance(Duration.ofSeconds(30));
        long before = jdbc.queryForObject("select row_version from feature_switch", Long.class);
        clock.advance(Duration.ofSeconds(1));
        here.set(false, OPERATOR);

        here.readFailed(new IllegalStateException("read C failed"));
        here.remember(new JdbcKillSwitch.Cached(new KillSwitch.State(true, null, START), before, START));

        assertThat(here.current().disabledReason()).isEqualTo(KillSwitch.Reason.OPERATOR);
    }

    /**
     * Review round 2 (security L-2, architecture L-5): a row version lowered out of band (re-seed, restore)
     * is followed once the cached entry is older than the TTL, and a write through the switch is answered
     * and cached as written.
     */
    @Test
    void aRowVersionLoweredOutOfBandIsFollowedAfterTheWindow() {
        JdbcKillSwitch here = instance(Duration.ofSeconds(30));
        here.set(false, OPERATOR);
        here.set(true, OPERATOR);
        assertThat(here.isAiEnabled()).isTrue();
        jdbc.update("update feature_switch set enabled = false, reason = 'OPERATOR', row_version = 0");

        clock.advance(Duration.ofSeconds(30));
        assertThat(here.current().disabledReason()).isEqualTo(KillSwitch.Reason.OPERATOR);

        jdbc.update("update feature_switch set enabled = true, reason = null, row_version = 0");
        assertThat(here.set(false, OPERATOR).aiEnabled()).isFalse();
        assertThat(here.isAiEnabled()).isFalse();
    }

    /** Reviews M2: the breaker is decided on the stored state, not on the cache, and is recorded once. */
    @Test
    void theBreakerIsRecordedOnceAndReplacesAnOperatorsOff() {
        JdbcKillSwitch here = instance(Duration.ofSeconds(30));
        JdbcKillSwitch other = instance(Duration.ofSeconds(30));
        assertThat(here.isAiEnabled()).isTrue(); // cached as on
        other.set(false, OPERATOR);

        here.tripProviderAuth();
        here.tripProviderAuth();
        other.tripProviderAuth();

        assertThat(events("PROVIDER_AUTH")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select reason from feature_switch", String.class)).isEqualTo("PROVIDER_AUTH");
        assertThat(here.current().disabledReason()).isEqualTo(KillSwitch.Reason.PROVIDER_AUTH);
        assertThat(jdbc.queryForObject("select count(*) from feature_switch_event where reason = 'PROVIDER_AUTH' "
                + "and actor is null", Integer.class)).isEqualTo(1);
    }

    /** Fail closed: no row or no table is the unreadable (off) state, never cached; a trip never throws. */
    @Test
    void aMissingStateIsOffAndATripNeverThrows() {
        JdbcKillSwitch here = instance(Duration.ofSeconds(30));
        jdbc.execute("alter table feature_switch rename to feature_switch_moved");
        try {
            assertThat(here.current()).isEqualTo(KillSwitch.State.unreadable());
            assertThatCode(here::tripProviderAuth).doesNotThrowAnyException();
            assertThatThrownBy(() -> here.set(true, OPERATOR)).isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.execute("alter table feature_switch_moved rename to feature_switch");
        }
        assertThat(here.isAiEnabled()).as("recovers at once").isTrue();
        assertThatThrownBy(() -> here.set(true, "cust-a")).isInstanceOf(IllegalArgumentException.class);
    }

    /** V13 constraints: no stored UNREADABLE, no second switch, no off without a reason, no system operator. */
    @Test
    void theTablesRefuseInconsistentRows() {
        assertThatThrownBy(() -> jdbc.update("update feature_switch set enabled = false, reason = 'UNREADABLE'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update feature_switch set enabled = false, reason = null"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into feature_switch (name, enabled, updated_at) "
                + "values ('other_switch', true, now())")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into feature_switch_event (name, enabled, reason, actor, "
                + "occurred_at) values ('ai_assistant', false, 'OPERATOR', null, now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into feature_switch_event (name, enabled, reason, actor, "
                + "occurred_at) values ('ai_assistant', false, 'PROVIDER_AUTH', ?, now())", OPERATOR))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
