package com.secureerp.agent.audit;

/**
 * Synchronous audit sink for egress decisions (AI_GATEWAY §2.5, ADR-012).
 *
 * <p>The gateway calls it directly, before every send attempt, instead of publishing an application
 * event (second review M1/L1): with an event, a missing or asynchronous listener would let bytes
 * leave without a record. With a constructor-injected port, a context without an implementation does
 * not start.
 *
 * <p>Contract for implementations: write the record durably in their own transaction before
 * returning, and throw on any failure. The gateway then sends nothing.
 */
@FunctionalInterface
public interface EgressAuditLog {

    void record(EgressDecisionEvent decision);
}
