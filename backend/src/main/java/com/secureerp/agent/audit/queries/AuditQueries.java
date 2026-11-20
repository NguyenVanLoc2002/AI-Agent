package com.secureerp.agent.audit.queries;

import com.secureerp.agent.audit.AuditEvent;
import java.time.Instant;
import java.util.List;

/**
 * Read side of audit for the security inspector (UC-07, MODULE_BOUNDARIES {@code AuditQueryApi}). Returns
 * the stored metadata only; every list is bounded by the caller's limit.
 */
public interface AuditQueries {

    /** One {@code egress_record} row: field names and codes, never values (ADR-012). */
    record EgressRow(String decision, int attempt, String policyCode, String honeytokenId, String blockedFieldPath,
            List<String> fieldNames, int byteLength, Instant occurredAt) {

        public EgressRow {
            fieldNames = List.copyOf(fieldNames);
        }
    }

    /** One {@code audit_event} row; {@code actor} and {@code actorRole} are null for a system event. */
    record EventRow(AuditEvent.Action action, AuditEvent.Resource resource, String outcome, String actor,
            String actorRole, String traceId, Instant occurredAt) {
    }

    /**
     * Egress decisions of one trace, oldest first: the per-call trace ID the provider saw, or the request ID of
     * the chat turn (A-45(h)).
     */
    List<EgressRow> egressOfTrace(String traceId, int limit);

    /** Audit events of one trace (a request), oldest first. */
    List<EventRow> eventsOfTrace(String traceId, int limit);

    /**
     * Audit events with {@code from <= occurredAt <= to}, newest first.
     *
     * @param action {@code null} for every action
     */
    List<EventRow> events(AuditEvent.Action action, Instant from, Instant to, int limit);
}
