package com.secureerp.agent.identity;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A sales team (RBAC_MATRIX: "team mình"). PoC default (owner decision 2026-11-19, D-08 still OPEN for
 * the real assignment logic): every SALES user belongs to one team and every customer is served by one
 * team, both from the demo configuration. A handoff, and a draft order waiting for reconciliation,
 * belongs to the team of the customer; staff of another team get the same 404 as for a missing one.
 * INTERNAL: a team name says nothing about a customer.
 */
public record Team(String value) {

    private static final Pattern FORMAT = Pattern.compile("^team-[a-z0-9]{1,16}$");

    public Team {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid team format");
        }
    }
}
