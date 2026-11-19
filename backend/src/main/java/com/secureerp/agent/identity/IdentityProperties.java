package com.secureerp.agent.identity;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    /**
     * @param team EP-08 (D-08 PoC default): the SALES user's team, or the team serving the customer.
     *             Required for SALES, not allowed for OPS and SECREV (they are not business readers)
     */
    record DemoUser(String username, Role role, Team team) {

        DemoUser {
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(role, "role");
            if ((role == Role.SALES && team == null) || ((role == Role.OPS || role == Role.SECREV) && team != null)) {
                throw new IllegalArgumentException("demo user team does not fit its role");
            }
        }
    }

    /** The configured team of a demo user; empty when the user is unknown or has none. */
    Optional<Team> teamOf(String username) {
        return demoUsers.stream().filter(user -> user.username().equals(username)).findFirst()
                .map(DemoUser::team);
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
