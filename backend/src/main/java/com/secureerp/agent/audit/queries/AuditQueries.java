package com.secureerp.agent.audit.queries;

import com.secureerp.agent.audit.AuditEvent;
import java.time.Instant;
import java.util.List;

/**
 * Read side of audit for the security inspector (UC-07, MODULE_BOUNDARIES {@code AuditQueryApi}). Returns
 * the stored metadata only; every list is bounded by the caller's limit.
 */
public interface AuditQueries {

    /**
     * One {@code egress_record} row: field names and codes, never values (ADR-012).
     *
     * @param direction {@code REQUEST}, or {@code RESPONSE} for a model answer the gateway withheld (A-59)
     */
    record EgressRow(String decision, int attempt, String policyCode, String honeytokenId, String blockedFieldPath,
            List<String> fieldNames, int byteLength, Instant occurredAt, String direction) {

        public EgressRow {
            fieldNames = List.copyOf(fieldNames);
        }
    }

    /**
     * One {@code audit_event} row; {@code actor} and {@code actorRole} are null for a system event.
     *
     * @param id             the row key, used for keyset paging only (not an identifier of any resource)
     * @param occurrences    identical separate events of the same minute collapsed into this row (AUDIT-FLOOD, V16)
     * @param lastOccurredAt the latest of them; {@code null} for a row that is never collapsed
     * @param lastTraceId    the trace ID of the latest of them; {@code null} for a row that is never collapsed
     */
    record EventRow(long id, AuditEvent.Action action, AuditEvent.Resource resource, String outcome, String actor,
            String actorRole, String traceId, Instant occurredAt, int occurrences, Instant lastOccurredAt,
            String lastTraceId) {
    }

    /**
     * Keyset bound for paging newest first: rows with {@code occurredAt < at}, or {@code occurredAt == at} and
     * {@code id < id}.
     */
    record Before(Instant at, long id) {
    }

    /**
     * Egress decisions of one trace, oldest first: the per-call trace ID the provider saw, or the request ID of
     * the chat turn (A-45(h)).
     */
    List<EgressRow> egressOfTrace(String traceId, int limit);

    /** Audit events of one trace (a request; the first or the latest event of a collapsed row), oldest first. */
    List<EventRow> eventsOfTrace(String traceId, int limit);

    /**
     * Audit events with at least one occurrence in {@code [from, to]}, newest first ({@code occurredAt}, then
     * {@code id}).
     *
     * @param action    {@code null} for every action
     * @param actorRole {@code null} for every role; else only events of actors with this role (AUDIT-FLOOD reviews L5)
     * @param before    {@code null} for the first page
     */
    List<EventRow> events(AuditEvent.Action action, String actorRole, Instant from, Instant to, Before before,
            int limit);
}
