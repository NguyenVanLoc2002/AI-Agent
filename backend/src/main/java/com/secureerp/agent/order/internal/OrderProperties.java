package com.secureerp.agent.order.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param submitTimeout how long a request may stay unfinished (registered but not sent, or sent
 *                      without a recorded answer) before it counts as an unknown outcome. Must be
 *                      longer than the call deadline of the adapter (DESIGN PROPOSAL: 2 minutes)
 */
@ConfigurationProperties("apas.order")
record OrderProperties(@DefaultValue("PT2M") Duration submitTimeout) {

    OrderProperties {
        if (submitTimeout == null || submitTimeout.isNegative() || submitTimeout.isZero()) {
            throw new IllegalArgumentException("apas.order.submit-timeout must be positive");
        }
    }
}
