package com.secureerp.agent.audit;

/**
 * Synchronous writer of the central {@code audit_event} (EP-09b, decision 2A). Called directly, never
 * through an application event, so a missing listener cannot lose a record (as {@link EgressAuditLog}).
 * The trace ID is the request's correlation ID ({@code platform.TraceIds}), empty outside a request.
 */
public interface AuditTrail {

    /**
     * For a business change: joins the caller's transaction, so the change and its record commit or roll back
     * together; throws on failure (fail closed) and when there is no transaction (a programming error).
     */
    void record(AuditEvent event);

    /**
     * For denials and reads: written in a transaction of its own, so it survives the caller's rollback.
     * Never throws: a failure is logged with its exception class only and the caller's outcome does not
     * change (a denial stays a denial). Call it outside any transaction: inside one it needs a second pooled
     * connection; throw {@link DeniedInTransaction} there and record after the transaction ends.
     */
    void recordSeparately(AuditEvent event);
}
