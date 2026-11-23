package com.secureerp.agent.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.audit.EgressAuditLog;
import com.secureerp.agent.audit.EgressDecisionEvent;
import com.secureerp.agent.catalog.spi.CatalogUnavailableException;
import com.secureerp.agent.catalog.spi.ExactProductQuery;
import com.secureerp.agent.erp.internal.MockErpConnector;
import com.secureerp.agent.erp.internal.MockErpFaultMode;
import com.secureerp.agent.gateway.api.SafeMessage;
import com.secureerp.agent.gateway.internal.EgressSerializer;
import com.secureerp.agent.policy.InboundSanitizer;
import com.secureerp.agent.policy.SentinelVariants;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * T-SEC01-CAPTURE, T-SEC01-ERR and T-SEC01-VIN end to end at gateway level (ADR-016): requests go
 * through the real adapter, the loopback proxy and the Fake provider; egress_record is written in a
 * real PostgreSQL.
 */
@ExtendWith(OutputCaptureExtension.class)
class GatewayEgressIT extends FakeProviderEnvironment {

    @Autowired
    AiGateway gateway;

    @Autowired
    InboundSanitizer sanitizer;

    @Test
    void tSec01CaptureAdapterProxyAndProviderSeeTheSameBytes() {
        String retried = EgressFixtures.traceId(7);
        failOnceFor(retried);

        GatewayResult first = gateway.complete(EgressFixtures.request(EgressFixtures.traceId(6),
                List.of(new SafeMessage(SafeMessage.Role.CUSTOMER, "Can ma phanh cho Vios 2019"))));
        GatewayResult second = gateway.complete(EgressFixtures.request(retried,
                List.of(new SafeMessage(SafeMessage.Role.CUSTOMER, "Loc gio Innova 2020 con khong"))));
        GatewayResult blocked = gateway.complete(EgressFixtures.request(EgressFixtures.traceId(8),
                List.of(new SafeMessage(SafeMessage.Role.CUSTOMER, "gia von " + SentinelVariants.COST))));

        assertThat(first).isInstanceOf(GatewayResult.Completed.class);
        assertThat(second).isInstanceOf(GatewayResult.Completed.class);
        assertThat(blocked).isEqualTo(new GatewayResult.Refused(GatewayResult.RefusalReason.DLP_EGRESS));

        List<byte[]> adapter = adapterCapture.attempts();
        List<byte[]> proxy = proxyBodies();
        List<byte[]> provider = providerBodies();
        assertThat(adapter).as("1 + 2 attempts (retry), blocked request never reaches the adapter").hasSize(3);
        assertThat(proxy).as("proxy is the only route").hasSameSizeAs(adapter);
        assertThat(provider).hasSameSizeAs(adapter);
        for (int i = 0; i < adapter.size(); i++) {
            assertThat(provider.get(i)).as("attempt %d byte-identical at provider", i).isEqualTo(adapter.get(i));
            assertThat(proxy.get(i)).isEqualTo(adapter.get(i));
        }
        assertThat(EgressScan.countWithSentinel(adapter.stream())).isZero();
        assertThat(EgressScan.countWithSentinel(provider.stream())).isZero();
        assertThat(provider).allSatisfy(body -> assertThat(EgressSerializer.structure(body)).isPresent());

        // Second review M3/L3: one PASS row per attempt, so the retry has its own row (attempt 2).
        assertThat(jdbc.queryForList("select decision || ':' || attempt from egress_record order by id", String.class))
                .containsExactly("PASS:1", "PASS:1", "PASS:2", "BLOCK:0");
        assertThat(jdbc.queryForObject("select count(*) from egress_record where decision = 'PASS'", Integer.class))
                .isEqualTo(provider.size());
    }

    @Test
    void tSec01ErrErpErrorWithSentinelReachesModelOnlyAsCode(CapturedOutput output) {
        MockErpConnector failingErp = new MockErpConnector(MockErpFaultMode.ERROR_WITH_SENTINEL);
        String errorSummary;
        try {
            failingErp.search(new ExactProductQuery.ByOe("04465-0K290"));
            throw new AssertionError("fault injection did not fail");
        } catch (CatalogUnavailableException e) {
            // What an agent may forward: the stable code, never the ERP message.
            errorSummary = "Tra cuu san pham that bai: " + e.code().name();
        }

        GatewayResult result = gateway.complete(EgressFixtures.request(EgressFixtures.traceId(9), List.of(
                new SafeMessage(SafeMessage.Role.CUSTOMER, "Can ma phanh 04465-0K290"),
                new SafeMessage(SafeMessage.Role.CONTEXT_SUMMARY, errorSummary))));

        assertThat(result).isInstanceOf(GatewayResult.Completed.class);
        assertThat(providerBodies()).singleElement()
                .satisfies(body -> assertThat(new String(body, StandardCharsets.UTF_8)).contains("SERVICE_UNAVAILABLE"))
                .satisfies(body -> assertThat(EgressScan.containsSentinel(body)).isFalse());
        assertThat(EgressScan.containsSentinel(output.getAll())).as("log output").isFalse();
        assertThat(output.getAll()).contains("erp.catalog.unavailable code=SERVICE_UNAVAILABLE");
    }

    @Test
    void tSec01VinFullVinTypedByCustomerLeavesOnlyAsWmi() {
        InboundSanitizer.Result sanitized = sanitizer.sanitize("Xe toi VIN JTDBT923X71234567, can ma phanh");
        assertThat(sanitized).isInstanceOf(InboundSanitizer.Sanitized.class);

        gateway.complete(EgressFixtures.request(EgressFixtures.traceId(10), List.of(
                new SafeMessage(SafeMessage.Role.CUSTOMER, ((InboundSanitizer.Sanitized) sanitized).text()))));

        String sent = new String(providerBodies().getFirst(), StandardCharsets.UTF_8);
        assertThat(sent).contains("JTD**************").doesNotContain("923X71234567");
    }

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    EgressAuditLog auditLog;

    @Autowired
    ApplicationContext context;

    /** Review H2/M1: a real insert failure in PostgreSQL means nothing is sent anywhere. */
    @Test
    void realAuditInsertFailureMeansNothingIsSent() {
        jdbc.execute("create or replace function sec01a_reject_audit() returns trigger language plpgsql as "
                + "$$ begin raise exception 'audit store unavailable'; end; $$");
        jdbc.execute("create trigger sec01a_reject_audit before insert on egress_record "
                + "for each row execute function sec01a_reject_audit()");
        try {
            GatewayResult result = gateway.complete(EgressFixtures.request(EgressFixtures.traceId(20),
                    List.of(new SafeMessage(SafeMessage.Role.CUSTOMER, "Can ma phanh cho Vios 2019"))));

            assertThat(result).isEqualTo(new GatewayResult.Refused(GatewayResult.RefusalReason.AUDIT_UNAVAILABLE));
            assertThat(adapterCapture.attempts()).isEmpty();
            assertThat(proxyBodies()).isEmpty();
            assertThat(providerBodies()).isEmpty();
        } finally {
            jdbc.execute("drop trigger sec01a_reject_audit on egress_record");
            jdbc.execute("drop function sec01a_reject_audit()");
        }
    }

    /** Review H2/M1: the audit row survives a rollback of the caller's transaction. */
    @Test
    void auditRowSurvivesCallerRollback() {
        String traceId = EgressFixtures.traceId(21);
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            gateway.complete(EgressFixtures.request(traceId,
                    List.of(new SafeMessage(SafeMessage.Role.CUSTOMER, "Loc gio Innova 2020"))));
            tx.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("select count(*) from egress_record where trace_id = ?", Integer.class, traceId))
                .isEqualTo(1);
        assertThat(providerBodies()).hasSize(1);
    }

    /** Review H2, second review M1: exactly one audit log bean; the row exists when record returns. */
    @Test
    void exactlyOneSynchronousAuditWriter() {
        assertThat(context.getBeansOfType(EgressAuditLog.class)).hasSize(1);
        String traceId = EgressFixtures.traceId(22);
        auditLog.record(new EgressDecisionEvent(traceId,
                EgressDecisionEvent.Decision.PASS, null, null, null, List.of("text"), 10,
                Instant.parse("2026-11-18T00:00:00Z"), 1, EgressDecisionEvent.Direction.REQUEST));

        assertThat(jdbc.queryForObject("select count(*) from egress_record where trace_id = ?", Integer.class, traceId))
                .isEqualTo(1);
    }

    /** SEC03-F3-AUDIT (A-59): a RESPONSE row is stored as such; V19 rejects a RESPONSE pass and unknown directions. */
    @Test
    void responseRowsAreBlocksOnly() {
        String traceId = EgressFixtures.traceId(23);
        auditLog.record(new EgressDecisionEvent(traceId, EgressDecisionEvent.Decision.BLOCK, "HONEYTOKEN", "HT-NAME-A",
                "$.text", List.of("text"), 40, Instant.parse("2026-11-18T00:00:00Z"), 1,
                EgressDecisionEvent.Direction.RESPONSE));

        assertThat(jdbc.queryForObject("select direction || ':' || decision || ':' || attempt from egress_record "
                + "where trace_id = ?", String.class, traceId)).isEqualTo("RESPONSE:BLOCK:1");
        String attemptCheck = "egress_record_attempt_matches_decision";
        assertThatThrownBy(() -> jdbc.update("insert into egress_record (trace_id, decision, attempt, field_names, "
                + "byte_length, occurred_at, direction) values (?, 'PASS', 1, 'text', 1, now(), 'RESPONSE')", traceId))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(attemptCheck);
        assertThatThrownBy(() -> jdbc.update("insert into egress_record (trace_id, decision, attempt, policy_code, "
                + "blocked_field_path, field_names, byte_length, occurred_at, direction) "
                + "values (?, 'BLOCK', 0, 'HONEYTOKEN', '$', 'text', 1, now(), 'RESPONSE')", traceId))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(attemptCheck);
        // The REQUEST branch is not widened: a request block still has attempt 0 (A-59 review).
        assertThatThrownBy(() -> jdbc.update("insert into egress_record (trace_id, decision, attempt, policy_code, "
                + "blocked_field_path, field_names, byte_length, occurred_at) "
                + "values (?, 'BLOCK', 1, 'HONEYTOKEN', '$', 'text', 1, now())", traceId))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(attemptCheck);
        // An unknown direction breaks both checks; either name is the rejection.
        assertThatThrownBy(() -> jdbc.update("insert into egress_record (trace_id, decision, attempt, field_names, "
                + "byte_length, occurred_at, direction) values (?, 'PASS', 1, 'text', 1, now(), 'OTHER')", traceId))
                .isInstanceOf(DataIntegrityViolationException.class);
        // A screen failure has no honeytoken ID.
        jdbc.update("insert into egress_record (trace_id, decision, attempt, policy_code, blocked_field_path, "
                + "field_names, byte_length, occurred_at, direction) "
                + "values (?, 'BLOCK', 1, 'SCREEN_FAILED', '$', 'text', 1, now(), 'RESPONSE')", traceId);
    }

    @Test
    void auditRowsContainMetadataOnly() {
        gateway.complete(EgressFixtures.request(EgressFixtures.traceId(11),
                List.of(new SafeMessage(SafeMessage.Role.CUSTOMER, "cong no " + SentinelVariants.CREDIT))));

        List<Map<String, Object>> rows = jdbc.queryForList("select * from egress_record");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.get("decision")).isEqualTo("BLOCK");
            assertThat(row.get("policy_code")).isEqualTo("HONEYTOKEN");
            assertThat(row.get("honeytoken_id")).isEqualTo("HT-CREDIT-01");
            assertThat(row.get("blocked_field_path")).isEqualTo("$.messages[0].text");
            assertThat(EgressScan.containsSentinel(row.toString().replace(row.get("id").toString(), ""))).isFalse();
        });
        assertThat(providerBodies()).isEmpty();
    }
}
