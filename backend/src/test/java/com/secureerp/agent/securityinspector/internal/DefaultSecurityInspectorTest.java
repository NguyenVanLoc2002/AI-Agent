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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** EP-09b: the role check in the service (the URL rule is defence in depth), the views and the query bounds. */
class DefaultSecurityInspectorTest {

    private static final Instant NOW = Instant.parse("2026-11-20T08:00:00Z");
    private static final String CUSTOMER_PSEUDONYM = "usr_CustomerPseudonymAAAA";
    private static final String SALES_PSEUDONYM = "usr_SalesPseudonymAAAAAAA";

    private final RecordingAuditTrail audit = new RecordingAuditTrail();
    private List<AuditQueries.EventRow> stored = List.of();
    private List<KillSwitchHistory.Change> changes = List.of();

    private final AuditQueries queries = new AuditQueries() {
        @Override
        public List<EgressRow> egressOfTrace(String traceId, int limit) {
            return List.of();
        }

        @Override
        public List<EventRow> eventsOfTrace(String traceId, int limit) {
            return stored;
        }

        @Override
        public List<EventRow> events(AuditEvent.Action action, Instant from, Instant to, int limit) {
            return stored.stream().filter(row -> action == null || row.action() == action).limit(limit).toList();
        }
    };
    private final ToolInvocationHistory tools = (turnId, limit) -> List.of();
    private final KillSwitchHistory switches = (from, to, limit) -> changes;
    private final DefaultSecurityInspector inspector = new DefaultSecurityInspector(queries, tools, switches, audit,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static SessionIdentity reviewer() {
        return IdentityFixtures.staffIdentity("Reviewer", Role.SECREV);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "SECREV", mode = EnumSource.Mode.EXCLUDE)
    void anyoneButASecurityReviewerIsRefusedAndTheDenialIsAudited(Role role) {
        SessionIdentity other = role == Role.CUST ? IdentityFixtures.sessionIdentity("NotRev", "NotRevCustomer")
                : IdentityFixtures.staffIdentity("NotRev" + role, role);

        assertThatThrownBy(() -> inspector.trace(other, UUID.randomUUID().toString()))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> inspector.events(other, new SecurityInspector.EventQuery(null, null, null, null)))
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
                new AuditQueries.EventRow(AuditEvent.Action.ACCESS_DENIED, AuditEvent.Resource.QUOTE,
                        "FORBIDDEN_RESOURCE", CUSTOMER_PSEUDONYM, "CUST", null, NOW.minusSeconds(2)),
                new AuditQueries.EventRow(AuditEvent.Action.HANDOFF_CLAIMED, AuditEvent.Resource.HANDOFF, "CLAIMED",
                        SALES_PSEUDONYM, "SALES", null, NOW.minusSeconds(1)));

        List<SecurityInspector.EventView> views =
                inspector.events(reviewer(), new SecurityInspector.EventQuery(null, null, null, null));

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
        stored = List.of(new AuditQueries.EventRow(AuditEvent.Action.HANDOFF_CLAIMED, AuditEvent.Resource.HANDOFF,
                "CLAIMED", SALES_PSEUDONYM, "SALES", null, NOW.minusSeconds(20)));
        changes = List.of(
                new KillSwitchHistory.Change(true, null, SALES_PSEUDONYM, NOW.minusSeconds(10)),
                new KillSwitchHistory.Change(false, KillSwitch.Reason.PROVIDER_AUTH, null, NOW.minusSeconds(30)));

        List<SecurityInspector.EventView> all =
                inspector.events(reviewer(), new SecurityInspector.EventQuery(null, null, null, 2));
        List<SecurityInspector.EventView> onlySwitches = inspector.events(reviewer(),
                new SecurityInspector.EventQuery(SecurityInspector.KILL_SWITCH_CHANGED, null, null, null));

        assertThat(all).extracting(SecurityInspector.EventView::outcome).containsExactly("ON", "CLAIMED");
        assertThat(onlySwitches).extracting(SecurityInspector.EventView::outcome)
                .containsExactly("ON", "OFF_PROVIDER_AUTH");
        assertThat(onlySwitches.get(1).actorRole()).as("the breaker is the system").isNull();
    }

    @Test
    void queriesAreBounded() {
        List<SecurityInspector.EventQuery> refused = List.of(
                new SecurityInspector.EventQuery(null, null, null, 0),
                new SecurityInspector.EventQuery(null, null, null, 101),
                new SecurityInspector.EventQuery("SELECT", null, null, null),
                new SecurityInspector.EventQuery(null, NOW.minus(Duration.ofDays(7)).minusSeconds(1), NOW, null),
                new SecurityInspector.EventQuery(null, NOW, NOW, null),
                new SecurityInspector.EventQuery(null, NOW.plusSeconds(1), null, null));
        for (SecurityInspector.EventQuery query : refused) {
            assertThatThrownBy(() -> inspector.events(reviewer(), query)).as(query.toString())
                    .isInstanceOfSatisfying(ApplicationException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
        assertThat(inspector.events(reviewer(),
                new SecurityInspector.EventQuery(null, NOW.minus(Duration.ofDays(7)), NOW, 100))).isEmpty();
        assertThatThrownBy(() -> inspector.trace(reviewer(), "../../etc"))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> inspector.trace(reviewer(), UUID.randomUUID().toString()))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
