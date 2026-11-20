package com.secureerp.agent.platform;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory {@link KillSwitch} for unit tests: settable state, counts breaker trips. {@link #unreadable}
 * follows the contract (returns the unreadable state); {@link #throwOnRead} breaks it on purpose, to test
 * that callers still fail closed.
 */
public class TestKillSwitch implements KillSwitch {

    private static final Instant SINCE = Instant.parse("2026-11-20T00:00:00Z");

    private volatile State state = new State(true, null, SINCE);
    private volatile boolean unreadable;
    public volatile boolean throwOnRead;
    public final AtomicInteger trips = new AtomicInteger();
    public final AtomicInteger reads = new AtomicInteger();
    /** When set, the switch turns off after this many reads (to flip it between two provider attempts). */
    public volatile int offAfterReads = -1;

    @Override
    public State current() {
        int read = reads.incrementAndGet();
        if (throwOnRead) {
            throw new IllegalStateException("switch store down");
        }
        if (unreadable) {
            return State.unreadable();
        }
        if (offAfterReads >= 0 && read > offAfterReads) {
            state = new State(false, Reason.OPERATOR, SINCE);
        }
        return state;
    }

    @Override
    public State set(boolean enabled, String actorPseudonym) {
        state = enabled ? new State(true, null, SINCE) : new State(false, Reason.OPERATOR, SINCE);
        return state;
    }

    @Override
    public void tripProviderAuth() {
        trips.incrementAndGet();
        state = new State(false, Reason.PROVIDER_AUTH, SINCE);
    }

    public void unreadable(boolean value) {
        unreadable = value;
    }
}
