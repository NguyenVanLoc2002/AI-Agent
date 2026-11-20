package com.secureerp.agent.audit;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One central audit event (EP-09b, ADR-012, A-45). Metadata only: what happened, to which kind of
 * resource, with which outcome code, by whom. There is deliberately no field for a resource identifier,
 * a text or a value (decision 6A, TH-15); the trace ID is taken from the request by the writer.
 */
public record AuditEvent(Action action, Resource resource, String outcome, AuditActor actor) {

    /** Closed list, mirrored by the V14 check constraint. */
    public enum Action {
        /** A role or ownership check refused the call (outcome FORBIDDEN or FORBIDDEN_RESOURCE). */
        ACCESS_DENIED,
        HANDOFF_CLAIMED,
        HANDOFF_RESOLVED,
        STAFF_MESSAGE_POSTED,
        STAFF_QUOTE_READ,
        STAFF_ORDER_DECIDED,
        ORDER_RECONCILED,
        QUOTE_CONFIRMATION,
        INSPECTOR_READ
    }

    /** Kind of resource only, never which one (decision 6A). */
    public enum Resource {
        HANDOFF,
        CONVERSATION,
        QUOTE,
        DRAFT_ORDER,
        KILL_SWITCH,
        SECURITY_TRACE,
        SECURITY_EVENTS,
        /** EP-09d (V17): areas of a URL refused by Spring Security; never the URL itself. */
        ENDPOINT_INTERNAL,
        ENDPOINT_OPS,
        ENDPOINT_SECURITY,
        ENDPOINT_DEMO,
        ENDPOINT_OTHER
    }

    /** Outcome codes used for denials. */
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String FORBIDDEN_RESOURCE = "FORBIDDEN_RESOURCE";

    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z_]{1,39}$");

    public AuditEvent {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(resource, "resource");
        if (outcome == null || !CODE.matcher(outcome).matches()) {
            throw new IllegalArgumentException("invalid audit field: outcome");
        }
    }

    /**
     * AUDIT-FLOOD (A-45(z)): whether identical separate events of a minute may be counted on one row. An accepted
     * confirmation is an order and stays one row per event (EP-09b reviews L1).
     */
    public boolean collapsible() {
        return !(action == Action.QUOTE_CONFIRMATION && "ACCEPTED".equals(outcome));
    }

    public static AuditEvent denied(Resource resource, String code, AuditActor actor) {
        return new AuditEvent(Action.ACCESS_DENIED, resource, code, actor);
    }
}
