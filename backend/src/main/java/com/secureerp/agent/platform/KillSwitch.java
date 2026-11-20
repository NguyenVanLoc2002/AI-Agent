package com.secureerp.agent.platform;

import java.time.Instant;
import java.util.Objects;

/**
 * The global AI kill switch and provider-auth breaker (UC-08, ADR-013 ACCEPTED 2026-11-20, A-44; SEC-06,
 * SEC-07). One switch for the whole PoC (decision 4A). It is checked at chat intake (DATA_FLOW step 2)
 * and by the gateway before every provider call. Fail closed: a state that cannot be read counts as
 * disabled.
 *
 * <p>The state is read from the database through a cache of at most {@code apas.kill-switch.cache-ttl}
 * (≤ 30 s, ADR-013); a change made on this instance takes effect here at once (the cache keeps the newest
 * row version), other instances see it within the TTL.
 */
public interface KillSwitch {

    /** Why the AI is off. */
    enum Reason {
        /** An operator switched it off (decision 3A). */
        OPERATOR,
        /** The provider answered 401/403; the breaker stays open until an operator closes it (decision 5A). */
        PROVIDER_AUTH,
        /** The state could not be read (fail closed; never stored). */
        UNREADABLE
    }

    /**
     * @param disabledReason {@code null} when enabled
     * @param since when the stored state last changed; {@code null} when it could not be read
     */
    record State(boolean aiEnabled, Reason disabledReason, Instant since) {

        public State {
            if (aiEnabled != (disabledReason == null)) {
                throw new IllegalArgumentException("a disabled state has a reason, an enabled one none");
            }
        }

        public static State unreadable() {
            return new State(false, Reason.UNREADABLE, null);
        }
    }

    /** The cached state. Never throws: a state that cannot be read is {@link State#unreadable()} (fail closed). */
    State current();

    /**
     * Switches the AI on or off for an operator. Switching it on also closes an open provider-auth
     * breaker. Recorded with the actor in {@code feature_switch_event} (decision 7A).
     *
     * @param actorPseudonym the operator's pseudonymous principal
     * @return the state after the change
     */
    State set(boolean enabled, String actorPseudonym);

    /**
     * Opens the provider-auth breaker (system actor), decided on the stored state under its lock: a no-op
     * when the breaker is already open; it replaces an operator's OFF, so the reason staff and operators see
     * is the key problem (EP-09a reviews, A-44(k)). Never throws.
     */
    void tripProviderAuth();

    /** Convenience for {@code current().aiEnabled()}. */
    default boolean isAiEnabled() {
        return Objects.requireNonNull(current(), "state").aiEnabled();
    }
}
