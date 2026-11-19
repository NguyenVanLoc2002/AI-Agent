package com.secureerp.agent.order;

import java.util.Objects;

/**
 * Published inside the transaction that changed a request's state outside a
 * {@link DraftOrders#dispatch} call (reconciliation, a request that timed out), so quote can mirror
 * it in the same transaction (ADR-007). Carries no customer or price data.
 */
public record DraftOrderStateChanged(String quoteRef, int quoteVersion, DraftOrderState state) {

    public DraftOrderStateChanged {
        Objects.requireNonNull(quoteRef, "quoteRef");
        Objects.requireNonNull(state, "state");
    }
}
