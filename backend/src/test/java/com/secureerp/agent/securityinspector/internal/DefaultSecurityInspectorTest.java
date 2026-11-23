package com.secureerp.agent.securityinspector.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.audit.AuditEvent;
import com.secureerp.agent.audit.RecordingAuditTrail;
import com.secureerp.agent.audit.queries.AuditQueries;
import com.secureerp.agent.identity.IdentityFixtures;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.platform.KillSwitch;
import com.secureerp.agent.platform.history.KillSwitchHistory;
import com.secureerp.agent.securityinspector.SecurityInspector;
import com.secureerp.agent.tools.history.ToolInvocationHistory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * EP-09b / AUDIT-FLOOD: the role check in the service (the URL rule is defence in depth), the views, the query
 * bounds and the keyset paging over the merged audit and switch lists.
 */
class DefaultSecurityInspectorTest {

    private static final Instant NOW = Instant.parse("2026-11-20T08:00:00Z");
    private static final String CUSTOMER_PSEUDONYM = "usr_CustomerPseudonymAAAAA";
    private static final String SALES_PSEUDONYM = "usr_SalesPseudonymAAAAAAAA";

    private final RecordingAuditTrail audit = new RecordingAuditTrail();
    private List<AuditQueries.EventRow> stored = List.of();
    private List<KillSwitchHistory.Change> changes = List.of();
    private int traceRows = 0;

    /** In-memory read ports with the same keyset semantics as the JDBC ones. */
    private final AuditQueries queries = new AuditQueries() {
        @Override
        public List<EgressRow> egressOfTrace(String traceId, int limit) {
            List<EgressRow> rows = new ArrayList<>();
            for (int i = 0; i < Math.min(traceRows, limit); i++) {
                rows.add(new EgressRow("PASS", 1, null, null, null, List.of("messages"), 10, NOW, "REQUEST"));
            }
            return rows;
        }

        @Override
        public List<EventRow> eventsOfTrace(String traceId, int limit) {
            return stored.stream().limit(limit).toList();
        }

        @Override
        public List<EventRow> events(AuditEvent.Action action, String actorRole, Instant from, Instant to,
                Before before, int limit) {
            return stored.stream()
                    .filter(row -> action == null || row.action() == action)
                    .filter(row -> actorRole == null || actorRole.equals(row.actorRole()))
                    .filter(row -> before == null || row.occurredAt().isBefore(before.at())
                            || (row.occurredAt().equals(before.at()) && row.id() < before.id()))
                    .sorted(Comparator.comparing(EventRow::occurredAt).reversed()
                            .thenComparing(Comparator.comparingLong(EventRow::id).reversed()))
                    .limit(limit).toList();
        }
    };
    private final ToolInvocationHistory tools = (turnId, limit) -> List.of();
    private final KillSwitchHistory switches = (from, to, beforeAt, beforeId, limit) -> changes.stream()
            .filter(change -> beforeAt == null || change.occurredAt().isBefore(beforeAt)
                    || (change.occurredAt().equals(beforeAt) && change.id() < beforeId))
            .sorted(Comparator.comparing(KillSwitchHistory.Change::occurredAt).reversed()
                    .thenComparing(Comparator.comparingLong(KillSwitchHistory.Change::id).reversed()))
            .limit(limit).toList();
    private final DefaultSecurityInspector inspector = new DefaultSecurityInspector(queries, tools, switches, audit,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static SessionIdentity reviewer() {
        return IdentityFixtures.staffIdentity("Reviewer", Role.SECREV);
    }

    private static SecurityInspector.EventQuery query(Integer limit, String before) {
        return new SecurityInspector.EventQuery(null, null, null, limit, before, null);
    }

    private static AuditQueries.EventRow row(long id, AuditEvent.Action action, String outcome, String actor,
            String role, Instant at) {
        return new AuditQueries.EventRow(id, action, AuditEvent.Resource.HANDOFF, outcome, actor, role, null, at, 1,
                null, null);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "SECREV", mode = EnumSource.Mode.EXCLUDE)
    void anyoneButASecurityReviewerIsRefusedAndTheDenialIsAudited(Role role) {
        SessionIdentity other = role == Role.CUST ? IdentityFixtures.sessionIdentity("NotRev", "NotRevCustomer")
                : IdentityFixtures.staffIdentity("NotRev" + role, role);

        assertThatThrownBy(() -> inspector.trace(other, UUID.randomUUID().toString()))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> inspector.events(other, query(null, null)))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(audit.separate()).extracting(AuditEvent::resource)
                .containsExactly(AuditEvent.Resource.SECURITY_TRACE, AuditEvent.Resource.SECURITY_EVENTS);
        assertThat(audit.separate()).allSatisfy(event -> {
            assertThat(event.action()).isEqualTo(AuditEvent.Action.ACCESS_DENIED);
            assertThat(event.actor().role()).isEqualTo(role.name());
        });
    }

    /** Decision 6A: a staff pseudonym is shown, a customer is shown by role only. */
    @Test
    void aCustomerActorIsShownByRoleOnly() {
        stored = List.of(
                row(1, AuditEvent.Action.ACCESS_DENIED, "FORBIDDEN_RESOURCE", CUSTOMER_PSEUDONYM, "CUST",
                        NOW.minusSeconds(2)),
                row(2, AuditEvent.Action.HANDOFF_CLAIMED, "CLAIMED", SALES_PSEUDONYM, "SALES", NOW.minusSeconds(1)));

        List<SecurityInspector.EventView> views = inspector.events(reviewer(), query(null, null)).events();

        assertThat(views).extracting(SecurityInspector.EventView::actor).containsExactly(SALES_PSEUDONYM, null);
        assertThat(views).extracting(SecurityInspector.EventView::actorRole).containsExactly("SALES", "CUST");
        assertThat(views.toString()).doesNotContain(CUSTOMER_PSEUDONYM);
        assertThat(audit.separate()).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditEvent.Action.INSPECTOR_READ);
            assertThat(event.outcome()).isEqualTo("READ");
        });
    }

    /** Decision 4A: switch changes and audit events in one list, newest first, cut to the limit. */
    @Test
    void switchChangesAndAuditEventsAreMergedNewestFirst() {
        stored = List.of(row(1, AuditEvent.Action.HANDOFF_CLAIMED, "CLAIMED", SALES_PSEUDONYM, "SALES",
                NOW.minusSeconds(20)));
        changes = List.of(
                new KillSwitchHistory.Change(1, true, null, SALES_PSEUDONYM, NOW.minusSeconds(10)),
                new KillSwitchHistory.Change(2, false, KillSwitch.Reason.PROVIDER_AUTH, null, NOW.minusSeconds(30)));

        List<SecurityInspector.EventView> all = inspector.events(reviewer(), query(2, null)).events();
        List<SecurityInspector.EventView> onlySwitches = inspector.events(reviewer(),
                new SecurityInspector.EventQuery(SecurityInspector.KILL_SWITCH_CHANGED, null, null, null, null, null))
                .events();

        assertThat(all).extracting(SecurityInspector.EventView::outcome).containsExactly("ON", "CLAIMED");
        assertThat(onlySwitches).extracting(SecurityInspector.EventView::outcome)
                .containsExactly("ON", "OFF_PROVIDER_AUTH");
        assertThat(onlySwitches.get(1).actorRole()).as("the breaker is the system").isNull();
    }

    /**
     * AUDIT-FLOOD (A-45(z)): keyset pages over the merged list visit every event once, also when audit and switch
     * rows share an instant; the last page has no cursor.
     */
    @Test
    void pagesVisitEveryEventOnceAcrossBothSources() {
        Instant tie = NOW.minusSeconds(5);
        stored = List.of(
                row(7, AuditEvent.Action.HANDOFF_CLAIMED, "CLAIMED", SALES_PSEUDONYM, "SALES", tie),
                row(8, AuditEvent.Action.HANDOFF_RESOLVED, "RESOLVED", SALES_PSEUDONYM, "SALES", tie),
                row(9, AuditEvent.Action.ACCESS_DENIED, "FORBIDDEN", SALES_PSEUDONYM, "SALES", NOW.minusSeconds(1)));
        changes = List.of(
                // Switch ids above the audit ids: a cursor that compared ids across the two tables would lose them.
                new KillSwitchHistory.Change(30, false, KillSwitch.Reason.OPERATOR, SALES_PSEUDONYM, tie),
                new KillSwitchHistory.Change(40, true, null, SALES_PSEUDONYM, tie));

        List<String> seen = new ArrayList<>();
        String before = null;
        int pages = 0;
        do {
            SecurityInspector.EventPage page = inspector.events(reviewer(), query(2, before));
            page.events().forEach(view -> seen.add(view.outcome()));
            before = page.next();
            pages++;
        } while (before != null && pages < 10);

        assertThat(seen).containsExactly("FORBIDDEN", "RESOLVED", "CLAIMED", "ON", "OFF_OPERATOR");
        assertThat(pages).isEqualTo(3);
    }

    /**
     * AUDIT-FLOOD reviews L5: a role filter finds the staff events under a flood of customer denials; switch changes
     * count as OPS, the breaker (system) never matches a role.
     */
    @Test
    void aRoleFilterKeepsOnlyThatRolesEvents() {
        stored = List.of(
                row(1, AuditEvent.Action.ACCESS_DENIED, "FORBIDDEN_RESOURCE", CUSTOMER_PSEUDONYM, "CUST",
                        NOW.minusSeconds(1)),
                row(2, AuditEvent.Action.HANDOFF_CLAIMED, "CLAIMED", SALES_PSEUDONYM, "SALES", NOW.minusSeconds(9)));
        changes = List.of(
                new KillSwitchHistory.Change(1, false, KillSwitch.Reason.OPERATOR, SALES_PSEUDONYM, NOW.minusSeconds(5)),
                new KillSwitchHistory.Change(2, false, KillSwitch.Reason.PROVIDER_AUTH, null, NOW.minusSeconds(3)));

        assertThat(inspector.events(reviewer(), new SecurityInspector.EventQuery(null, null, null, null, null,
                "SALES")).events()).extracting(SecurityInspector.EventView::outcome).containsExactly("CLAIMED");
        assertThat(inspector.events(reviewer(), new SecurityInspector.EventQuery(null, null, null, null, null,
                "OPS")).events()).extracting(SecurityInspector.EventView::outcome).containsExactly("OFF_OPERATOR");
        SecurityInspector.EventPage all = inspector.events(reviewer(), query(4, null));
        assertThat(all.events()).hasSize(4);
        assertThat(all.next()).as("nothing after a page that holds everything").isNull();
    }

    /** AUDIT-FLOOD: a collapsed row carries its count; a trace cut at its cap says so. */
    @Test
    void collapsedRowsShowTheirCountAndACutTraceIsFlagged() {
        stored = List.of(new AuditQueries.EventRow(1, AuditEvent.Action.ACCESS_DENIED, AuditEvent.Resource.QUOTE,
                "FORBIDDEN_RESOURCE", CUSTOMER_PSEUDONYM, "CUST", null, NOW.minusSeconds(50), 500,
                NOW.minusSeconds(1), null));

        SecurityInspector.EventView view = inspector.events(reviewer(), query(null, null)).events().getFirst();
        assertThat(view.occurrences()).isEqualTo(500);
        assertThat(view.lastOccurredAt()).isEqualTo(NOW.minusSeconds(1));

        traceRows = SecurityInspector.MAX_TRACE_ROWS + 1;
        SecurityInspector.Trace cut = inspector.trace(reviewer(), UUID.randomUUID().toString());
        assertThat(cut.truncated()).isTrue();
        assertThat(cut.egress()).hasSize(SecurityInspector.MAX_TRACE_ROWS);
        traceRows = SecurityInspector.MAX_TRACE_ROWS;
        assertThat(inspector.trace(reviewer(), UUID.randomUUID().toString()).truncated()).isFalse();
    }

    @Test
    void queriesAreBounded() {
        List<SecurityInspector.EventQuery> refused = List.of(
                query(0, null),
                query(101, null),
                new SecurityInspector.EventQuery("SELECT", null, null, null, null, null),
                new SecurityInspector.EventQuery(null, null, null, null, null, "ADMIN"),
                new SecurityInspector.EventQuery(null, NOW.minus(Duration.ofDays(7)).minusSeconds(1), NOW, null, null,
                        null),
                new SecurityInspector.EventQuery(null, NOW, NOW, null, null, null),
                new SecurityInspector.EventQuery(null, NOW.plusSeconds(1), null, null, null, null),
                query(null, "not-a-cursor"),
                query(null, "1.000000000A1"),
                query(null, "1763625600.000000000X1"),
                query(null, "1763625600.000000000A"));
        for (SecurityInspector.EventQuery query : refused) {
            assertThatThrownBy(() -> inspector.events(reviewer(), query)).as(query.toString())
                    .isInstanceOfSatisfying(ApplicationException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
        assertThat(inspector.events(reviewer(), new SecurityInspector.EventQuery(null, NOW.minus(Duration.ofDays(7)),
                NOW, 100, null, null)).events()).isEmpty();
        assertThatThrownBy(() -> inspector.trace(reviewer(), "../../etc"))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> inspector.trace(reviewer(), UUID.randomUUID().toString()))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
