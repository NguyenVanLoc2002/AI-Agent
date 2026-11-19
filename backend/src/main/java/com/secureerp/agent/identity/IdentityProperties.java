package com.secureerp.agent.identity;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Session limits and the synthetic demo users (ADR-010; owner decisions 2026-11-18 and 2026-11-19).
 * The demo password comes from the environment only and is never logged; with no password there is no
 * demo user (every login fails).
 */
@ConfigurationProperties("apas.identity")
record IdentityProperties(Duration idleTimeout, Duration absoluteTimeout, String demoPassword,
        List<DemoUser> demoUsers) {

    static final int MIN_PASSWORD_CHARS = 12;

    record DemoUser(String username, Role role) {

        DemoUser {
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(role, "role");
        }
    }

    IdentityProperties {
        idleTimeout = Objects.requireNonNullElse(idleTimeout, Duration.ofMinutes(30));
        absoluteTimeout = Objects.requireNonNullElse(absoluteTimeout, Duration.ofHours(8));
        if (idleTimeout.isNegative() || idleTimeout.isZero() || absoluteTimeout.compareTo(idleTimeout) < 0) {
            throw new IllegalArgumentException("invalid session timeouts");
        }
        demoPassword = demoPassword == null ? "" : demoPassword;
        if (!demoPassword.isEmpty() && demoPassword.length() < MIN_PASSWORD_CHARS) {
            throw new IllegalArgumentException("demo password too short");
        }
        demoUsers = demoUsers == null ? List.of() : List.copyOf(demoUsers);
    }

    @Override
    public String toString() {
        return "IdentityProperties[idle=" + idleTimeout + ", absolute=" + absoluteTimeout + ", users="
                + demoUsers.size() + "]";
    }
}
