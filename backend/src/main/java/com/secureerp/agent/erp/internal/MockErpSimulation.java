package com.secureerp.agent.erp.internal;

import java.util.List;
import java.util.Objects;

/**
 * Controls of the simulated ERP (Mock only, ERP_CONNECTOR §5): tests change a price, stock or credit
 * status between a quote and its confirmation, inject draft-order faults and count what the ERP
 * wrote. Synthetic data only. No endpoint and no other module can reach it (ARCH-03); a real adapter
 * has nothing like it.
 */
public final class MockErpSimulation {

    private final MockErpBackend backend;

    MockErpSimulation(MockErpBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    /** Restores the configured fault mode and the synthetic data, and forgets every draft. */
    public void reset() {
        backend.reset();
    }

    public void failReads(MockErpFaultMode mode) {
        backend.failReads(mode);
    }

    public void failOrders(MockErpOrderFault fault) {
        backend.failOrders(fault);
    }

    /** @param unitPrice JSON number text, e.g. {@code "555444000"} */
    public void listPrice(String customerKey, String sku, String unitPrice) {
        backend.priceOverride(customerKey, sku, Objects.requireNonNull(unitPrice, "unitPrice"));
    }

    public void delistPrice(String customerKey, String sku) {
        backend.priceOverride(customerKey, sku, null);
    }

    public void stock(String sku, int quantity) {
        backend.stockOverride(sku, quantity);
    }

    /** @param status the raw ledger status, e.g. {@code OK} or {@code BLOCKED} */
    public void creditStatus(String customerKey, String status) {
        backend.creditOverride(customerKey, Objects.requireNonNull(status, "status"));
    }

    /** How many drafts the simulated ERP holds. */
    public int draftCount() {
        return backend.drafts().size();
    }

    public List<String> draftExternalRefs() {
        return backend.drafts().stream().map(MockErpBackend.Draft::externalRef).toList();
    }

    /** How many drafts were created with an idempotency key (BR-18). */
    public long draftsWithIdempotencyKey() {
        return backend.drafts().stream().filter(draft -> draft.idempotencyKey() != null).count();
    }

    /** How many create calls reached the simulated ERP, whatever their result. */
    public int createCalls() {
        return backend.createCalls();
    }

    public int lookupCalls() {
        return backend.lookupCalls();
    }
}
