package com.secureerp.agent.securityinspector.internal;

import com.secureerp.agent.audit.AuditEvent;
import com.secureerp.agent.audit.AuditTrail;
import com.secureerp.agent.audit.queries.AuditQueries;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.platform.KillSwitch;
import com.secureerp.agent.platform.TraceIds;
import com.secureerp.agent.platform.history.KillSwitchHistory;
import com.secureerp.agent.securityinspector.SecurityInspector;
import com.secureerp.agent.tools.history.ToolInvocationHistory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SECREV only (RBAC_MATRIX "Xem security trace (redacted)"), checked here as well as at the URL. Reads the
 * metadata tables through the read ports of the modules that own them and maps them to views without any
 * customer identifier: a customer actor is shown by role only (decision 6A).
 */
@Service
class DefaultSecurityInspector implements SecurityInspector {

    private static final Logger log = LoggerFactory.getLogger(DefaultSecurityInspector.class);
    static final Duration MAX_WINDOW = Duration.ofDays(7);
    static final Duration DEFAULT_WINDOW = Duration.ofHours(24);
    /** Bounds a timestamp the database can store and the PoC can have written (EP-09b reviews, security L-3). */
    static final Instant EARLIEST = Instant.parse("2000-01-01T00:00:00Z");
    static final Duration CLOCK_SKEW = Duration.ofDays(1);
    /** Roles whose pseudonym the reviewer sees (staff); the pseudonym of a customer is never shown. */
    private static final Set<String> STAFF_ROLES = Set.of(Role.SALES.name(), Role.OPS.name(), Role.SECREV.name());
    private static final Set<String> ACTIONS = Stream.concat(
                    Arrays.stream(AuditEvent.Action.values()).map(Enum::name), Stream.of(KILL_SWITCH_CHANGED))
            .collect(Collectors.toUnmodifiableSet());

    private final AuditQueries audits;
    private final ToolInvocationHistory tools;
    private final KillSwitchHistory switches;
    private final AuditTrail audit;
    private final Clock clock;

    DefaultSecurityInspector(AuditQueries audits, ToolInvocationHistory tools, KillSwitchHistory switches,
            AuditTrail audit, Clock clock) {
        this.audits = audits;
        this.tools = tools;
        this.switches = switches;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public Trace trace(SessionIdentity reviewer, String traceId) {
        requireReviewer(reviewer, AuditEvent.Resource.SECURITY_TRACE);
        if (traceId == null || !TraceIds.FORMAT.matcher(traceId).matches()) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        Trace trace = new Trace(traceId,
                audits.egressOfTrace(traceId, MAX_TRACE_ROWS).stream()
                        .map(row -> new EgressView(row.decision(), row.attempt(), row.policyCode(), row.honeytokenId(),
                                row.blockedFieldPath(), row.fieldNames(), row.byteLength(), row.occurredAt()))
                        .toList(),
                tools.ofTurn(traceId, MAX_TRACE_ROWS).stream()
                        .map(row -> new ToolView(row.toolName(), row.decision(), row.reasonCode(), row.occurredAt()))
                        .toList(),
                audits.eventsOfTrace(traceId, MAX_TRACE_ROWS).stream().map(DefaultSecurityInspector::view).toList());
        boolean empty = trace.egress().isEmpty() && trace.tools().isEmpty() && trace.events().isEmpty();
        String outcome = empty ? "NOT_FOUND" : "READ";
        audit.recordSeparately(new AuditEvent(AuditEvent.Action.INSPECTOR_READ, AuditEvent.Resource.SECURITY_TRACE,
                outcome, reviewer.auditActor()));
        log.info("securityinspector.trace outcome={}", outcome);
        if (empty) {
            throw new ApplicationException(ErrorCode.NOT_FOUND);
        }
        return trace;
    }

    @Override
    public List<EventView> events(SessionIdentity reviewer, EventQuery query) {
        requireReviewer(reviewer, AuditEvent.Resource.SECURITY_EVENTS);
        Objects.requireNonNull(query, "query");
        Instant now = clock.instant();
        Instant to = query.to() == null ? now : query.to();
        // Round-2 review L-1: bound "to" before any arithmetic on it, so an extreme date is a 400, never a 500.
        if (to.isBefore(EARLIEST) || to.isAfter(now.plus(CLOCK_SKEW))) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        Instant from = query.from() == null ? to.minus(DEFAULT_WINDOW) : query.from();
        int limit = query.limit() == null ? DEFAULT_EVENTS : query.limit();
        if (from.isBefore(EARLIEST)
                || !from.isBefore(to) || Duration.between(from, to).compareTo(MAX_WINDOW) > 0
                || limit < 1 || limit > MAX_EVENTS
                || (query.action() != null && !ACTIONS.contains(query.action()))) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        boolean switchesOnly = KILL_SWITCH_CHANGED.equals(query.action());
        List<EventView> found = new ArrayList<>();
        if (!switchesOnly) {
            AuditEvent.Action action = query.action() == null ? null : AuditEvent.Action.valueOf(query.action());
            audits.events(action, from, to, limit).stream().map(DefaultSecurityInspector::view).forEach(found::add);
        }
        if (query.action() == null || switchesOnly) {
            switches.changes(from, to, limit).stream().map(DefaultSecurityInspector::view).forEach(found::add);
        }
        List<EventView> newest = found.stream()
                .sorted(Comparator.comparing(EventView::occurredAt).reversed())
                .limit(limit)
                .toList();
        audit.recordSeparately(new AuditEvent(AuditEvent.Action.INSPECTOR_READ, AuditEvent.Resource.SECURITY_EVENTS,
                "READ", reviewer.auditActor()));
        log.info("securityinspector.events count={}", newest.size());
        return newest;
    }

    private void requireReviewer(SessionIdentity reviewer, AuditEvent.Resource resource) {
        Objects.requireNonNull(reviewer, "reviewer");
        if (reviewer.role() != Role.SECREV) {
            log.warn("securityinspector.access denied code=FORBIDDEN");
            audit.recordSeparately(AuditEvent.denied(resource, AuditEvent.FORBIDDEN, reviewer.auditActor()));
            throw new ApplicationException(ErrorCode.FORBIDDEN);
        }
    }

    static EventView view(AuditQueries.EventRow row) {
        String actor = row.actorRole() != null && STAFF_ROLES.contains(row.actorRole()) ? row.actor() : null;
        return new EventView(row.action().name(), row.resource().name(), row.outcome(), row.actorRole(), actor,
                row.traceId(), row.occurredAt());
    }

    static EventView view(KillSwitchHistory.Change change) {
        String outcome = change.aiEnabled() ? "ON"
                : change.reason() == KillSwitch.Reason.PROVIDER_AUTH ? "OFF_PROVIDER_AUTH" : "OFF_OPERATOR";
        // Only an operator changes the switch by hand; the system actor (null) is the provider-auth breaker.
        String role = change.actor() == null ? null : Role.OPS.name();
        return new EventView(KILL_SWITCH_CHANGED, AuditEvent.Resource.KILL_SWITCH.name(), outcome, role,
                change.actor(), null, change.occurredAt());
    }
}
