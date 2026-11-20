package com.secureerp.agent.order.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param submitTimeout how long a request may stay unfinished (registered but not sent, or sent
 *                      without a recorded answer) before it counts as an unknown outcome. Must be
 *                      longer than the call deadline of the adapter (DESIGN PROPOSAL: 2 minutes)
 * @param sweeper       the background sweep that turns unfinished requests past the timeout into unknown
 *                      outcomes without waiting for a reader (EP-09c, A-41(l), A-46 decision 2A)
 */
@ConfigurationProperties("apas.order")
record OrderProperties(@DefaultValue("PT2M") Duration submitTimeout, @DefaultValue Sweeper sweeper) {

    /** Shortest accepted sweep interval: a smaller one would only load the database. */
    static final Duration MIN_SWEEP_INTERVAL = Duration.ofMillis(100);

    /**
     * Whether the sweeper runs is decided by {@code apas.order.sweeper.enabled} on {@link DraftOrderSweeper} alone
     * (one source, EP-09c round-2 review L1). The interval is validated even when the sweeper is off (fail
     * closed: a later switch-on cannot start with an invalid value).
     *
     * @param interval pause between two sweeps (fixed delay); not longer than the submit timeout
     */
    record Sweeper(@DefaultValue("PT30S") Duration interval) {
    }

    OrderProperties {
        if (submitTimeout == null || submitTimeout.isNegative() || submitTimeout.isZero()) {
            throw new IllegalArgumentException("apas.order.submit-timeout must be positive");
        }
        if (sweeper == null || sweeper.interval() == null || sweeper.interval().compareTo(MIN_SWEEP_INTERVAL) < 0
                || sweeper.interval().compareTo(submitTimeout) > 0) {
            throw new IllegalArgumentException(
                    "apas.order.sweeper.interval must be between 100 ms and apas.order.submit-timeout");
        }
    }
}
