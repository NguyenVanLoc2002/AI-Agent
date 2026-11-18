package com.secureerp.agent.gateway.api;

/**
 * Coarse response limit chosen by the caller. The adapter maps it to concrete token and timeout
 * values from configuration. An enum instead of numeric limits keeps numbers out of the egress
 * types (ARCH-05 numeric allowlist). DESIGN PROPOSAL (SEC-01a); replaces the contract's
 * {@code limits} field.
 */
public enum ResponseBudget {
    SHORT,
    STANDARD
}
