package com.secureerp.agent.audit;

import java.util.Objects;

/**
 * Thrown inside a transaction when an ownership check refuses the call. The caller catches it after the
 * transaction has ended and only then records the denial with {@link AuditTrail#recordSeparately}, so a
 * denial never waits for a second pooled connection while the first is held (EP-09b reviews, security M-1,
 * architecture M1: concurrent probes would otherwise exhaust the pool and lose their own records).
 *
 * <p>Intra-class only: it is thrown and caught by the same service (round-2 review L1). A caller that let it
 * escape would answer 500 and lose the denial record; it has no message and no stack trace on purpose.
 */
public final class DeniedInTransaction extends RuntimeException {

    private final transient AuditEvent event;

    public DeniedInTransaction(AuditEvent event) {
        super(null, null, false, false);
        this.event = Objects.requireNonNull(event, "event");
    }

    public AuditEvent event() {
        return event;
    }
}
