package com.secureerp.agent.securityinspector;

import com.secureerp.agent.identity.SessionIdentity;
import java.time.Instant;
import java.util.List;

/**
 * Redacted security traces for SECURITY_REVIEWER (UC-07, TH-15; EP-09b, A-45). Only codes, field names,
 * tool names, roles, staff pseudonyms and trace IDs (HTTP correlation IDs): never a message, a price, a value, a
 * customer, conversation, quote or draft order request identifier (decision 6A). Failures are {@code ApplicationException} codes: 403 for
 * anyone but SECREV, 404 for a trace without records, 400 for a malformed query. Every read is audited.
 */
public interface SecurityInspector {

    /** Most rows returned per section of a trace. */
    int MAX_TRACE_ROWS = 200;
    /** Most events returned by one {@link #events} query (decision 5A). */
    int MAX_EVENTS = 100;
    int DEFAULT_EVENTS = 50;

    /** Pseudo-action of a kill switch change (stored in {@code feature_switch_event}, decision 4A). */
    String KILL_SWITCH_CHANGED = "KILL_SWITCH_CHANGED";

    record EgressView(String decision, int attempt, String policyCode, String honeytokenId, String blockedFieldPath,
            List<String> fieldNames, int byteLength, Instant occurredAt) {
    }

    record ToolView(String toolName, String decision, String reasonCode, Instant occurredAt) {
    }

    /**
     * @param actor the pseudonym of a staff actor (SALES, OPS, SECREV); {@code null} for a customer and for
     *              the system (decision 6A: no customer identifiers)
     */
    record EventView(String action, String resource, String outcome, String actorRole, String actor, String traceId,
            Instant occurredAt) {
    }

    /** What one trace did: egress decisions and tool calls of a chat turn, audit events of a request. */
    record Trace(String traceId, List<EgressView> egress, List<ToolView> tools, List<EventView> events) {
    }

    /**
     * @param action {@code null} for every action; else an audit action name or {@value #KILL_SWITCH_CHANGED}
     * @param from   {@code null}: 24 h before {@code to}
     * @param to     {@code null}: now; the window is at most 7 days (decision 7A)
     * @param limit  {@code null}: {@value #DEFAULT_EVENTS}; 1..{@value #MAX_EVENTS}
     */
    record EventQuery(String action, Instant from, Instant to, Integer limit) {
    }

    Trace trace(SessionIdentity reviewer, String traceId);

    /** Newest first. */
    List<EventView> events(SessionIdentity reviewer, EventQuery query);
}
