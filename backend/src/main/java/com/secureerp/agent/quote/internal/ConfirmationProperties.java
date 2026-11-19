package com.secureerp.agent.quote.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param maxAttemptsPerHour confirmation attempts per customer per rolling hour, whatever their
 *                           outcome (TH-26: token guessing; each attempt can also cost ERP calls).
 *                           DESIGN PROPOSAL: 20
 */
@ConfigurationProperties("apas.quote.confirmation")
record ConfirmationProperties(@DefaultValue("20") int maxAttemptsPerHour) {

    ConfirmationProperties {
        if (maxAttemptsPerHour < 1) {
            throw new IllegalArgumentException("apas.quote.confirmation.max-attempts-per-hour must be positive");
        }
    }
}
