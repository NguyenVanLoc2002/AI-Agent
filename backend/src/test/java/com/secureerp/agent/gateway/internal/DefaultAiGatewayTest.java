package com.secureerp.agent.gateway.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.audit.EgressAuditLog;
import com.secureerp.agent.audit.EgressDecisionEvent;
import com.secureerp.agent.gateway.EgressFixtures;
import com.secureerp.agent.gateway.EgressScan;
import com.secureerp.agent.gateway.GatewayResult.FailureReason;
import com.secureerp.agent.gateway.GatewayResult.RefusalReason;
import com.secureerp.agent.gateway.GatewayResult;
import com.secureerp.agent.gateway.api.AvailabilityBucket;
import com.secureerp.agent.gateway.api.ModelRequest;
import com.secureerp.agent.gateway.api.ProductRef;
import com.secureerp.agent.gateway.api.ProductSearchResult;
import com.secureerp.agent.gateway.api.Provenance;
import com.secureerp.agent.gateway.api.ResponseBudget;
import com.secureerp.agent.gateway.api.SafeMessage;
import com.secureerp.agent.gateway.api.TechnicalContext;
import com.secureerp.agent.gateway.internal.ModelProviderPort.ProviderReply;
import com.secureerp.agent.gateway.internal.ModelProviderPort.ProviderTransportException;
import com.secureerp.agent.platform.KillSwitch;
import com.secureerp.agent.platform.TestKillSwitch;
import com.secureerp.agent.policy.EgressPolicy;
import com.secureerp.agent.policy.EgressVerdict;
import com.secureerp.agent.policy.ResponseScreen;
import com.secureerp.agent.policy.SentinelVariants;
import com.secureerp.agent.policy.internal.DefaultEgressPolicy;
import com.secureerp.agent.policy.internal.DefaultResponseScreen;
import com.secureerp.agent.policy.internal.SensitiveDataDetector;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Gateway pipeline: T-SEC01-VARIANTS (5 sources), fail-closed paths, bounded retries, strict parsing. */
@ExtendWith(OutputCaptureExtension.class)
class DefaultAiGatewayTest {

    private static final byte[] OK_RESPONSE = "{\"text\":\"Da tim thay 1 san pham phu hop.\"}"
            .getBytes(StandardCharsets.UTF_8);

    private final RecordingProvider provider = new RecordingProvider();
    private final List<Object> published = new ArrayList<>();
    private final EgressAuditLog publisher = published::add;
    private final EgressPolicy policy = new DefaultEgressPolicy(new SensitiveDataDetector());
    private final ResponseScreen screen = new DefaultResponseScreen(new SensitiveDataDetector());
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-18T00:00:00Z"), ZoneOffset.UTC);
    private final TestKillSwitch killSwitch = new TestKillSwitch();

    private DefaultAiGateway gateway() {
        return new DefaultAiGateway(policy, screen, provider, publisher, clock, killSwitch);
    }

    @Test
    void cleanRequestIsSentOnceAsTheScannedBytesAndAudited() {
        provider.replies.add(new ProviderReply(200, OK_RESPONSE));
        ModelRequest request = EgressFixtures.request("Toi can ma phanh truoc cho Vios 2019");

        GatewayResult result = gateway().complete(request);

        assertThat(result).isInstanceOf(GatewayResult.Completed.class);
        assertThat(provider.sent).hasSize(1);
        assertThat(provider.sent.get(0)).isEqualTo(EgressSerializer.serialize(request));
        EgressDecisionEvent event = singleEvent();
        assertThat(event.decision()).isEqualTo(EgressDecisionEvent.Decision.PASS);
        assertThat(event.attempt()).isEqualTo(1);
        assertThat(event.byteLength()).isEqualTo(provider.sent.get(0).length);
        assertThat(event.fieldNames()).contains("messages", "text", "verifiedProducts", "type")
                .allMatch(EgressSerializer.ALLOWED_PROPERTY_NAMES::contains);
    }

    /**
     * SEC03-F3 (A-58): a honeytoken in the answer text, a tool proposal's name or arguments, or assembled across them
     * withholds the answer; the provider is called once (no retry) and the log names the honeytoken ID, never the
     * answer. SEC03-F3-AUDIT (A-59): one RESPONSE row with the exact structural path of the value.
     */
    static Stream<Arguments> withheldAnswers() {
        return Stream.of(
                Arguments.of("plain text", "{\"text\":\"Khach ZZHoney CustomerA da hoi\"}", "HT-NAME-A", "$.text"),
                Arguments.of("cyrillic text", "{\"text\":\"Khach ZZH\\u043eney CustomerA da hoi\"}", "HT-NAME-A",
                        "$.text"),
                Arguments.of("base64 text", "{\"text\":\"Ma tham chieu: WlpIb25leSBDdXN0b21lckE=\"}", "HT-NAME-A",
                        "$.text"),
                Arguments.of("digit token", "{\"text\":\"Gia von 987654321\"}", "HT-COST-01", "$.text"),
                Arguments.of("tool arguments", "{\"toolProposals\":[{\"name\":\"searchVerifiedProduct\",\"argumentsJson\":"
                        + "\"{\\\"category\\\":\\\"ZZHoney CustomerA\\\",\\\"vehicleModel\\\":\\\"Vios\\\"}\"}]}",
                        "HT-NAME-A", "$.toolProposals[0].argumentsJson"),
                Arguments.of("tool name", "{\"toolProposals\":[{\"name\":\"searchVerifiedProduct\",\"argumentsJson\":"
                        + "\"{}\"},{\"name\":\"zzHoneyCustomerA\",\"argumentsJson\":\"{}\"}]}", "HT-NAME-A",
                        "$.toolProposals[1].name"),
                Arguments.of("across values", "{\"text\":\"Khach ZZHoney\",\"toolProposals\":[{\"name\":"
                        + "\"customerAsk\",\"argumentsJson\":\"{}\"}]}", "HT-NAME-A", "$"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("withheldAnswers")
    void anAnswerCarryingAHoneytokenIsWithheld(String name, String answer, String honeytokenId, String path,
            CapturedOutput output) {
        provider.replies.add(new ProviderReply(200, answer.getBytes(StandardCharsets.UTF_8)));

        GatewayResult result = gateway().complete(EgressFixtures.request("Toi can ma phanh truoc"));

        assertThat(result).isEqualTo(new GatewayResult.Refused(RefusalReason.OUTPUT_HONEYTOKEN));
        assertThat(provider.sent).hasSize(1);
        assertThat(output.getAll()).contains("gateway.response decision=BLOCK policy=HONEYTOKEN honeytokenId=" + honeytokenId)
                .doesNotContain("ZZHoney").doesNotContain("CustomerA").doesNotContain("987654321");
        assertThat(published).hasSize(2);
        assertThat(((EgressDecisionEvent) published.get(0)).direction()).isEqualTo(EgressDecisionEvent.Direction.REQUEST);
        EgressDecisionEvent withheld = (EgressDecisionEvent) published.get(1);
        assertThat(withheld.direction()).isEqualTo(EgressDecisionEvent.Direction.RESPONSE);
        assertThat(withheld.decision()).isEqualTo(EgressDecisionEvent.Decision.BLOCK);
        assertThat(withheld.attempt()).isEqualTo(1);
        assertThat(withheld.policyCode()).isEqualTo("HONEYTOKEN");
        assertThat(withheld.honeytokenId()).isEqualTo(honeytokenId);
        assertThat(withheld.blockedFieldPath()).isEqualTo(path);
        assertThat(withheld.toString()).doesNotContain("ZZHoney").doesNotContain("987654321");
    }

    /** SEC03-F3-AUDIT (A-59) review: an answer to the retry is recorded with the attempt that produced it. */
    @Test
    void aWithheldAnswerToTheRetryCarriesItsAttempt() {
        provider.replies.add(new ProviderReply(503, new byte[0]));
        provider.replies.add(new ProviderReply(200, "{\"text\":\"Khach ZZHoney CustomerA\"}".getBytes(StandardCharsets.UTF_8)));

        assertThat(gateway().complete(EgressFixtures.request("Toi can ma phanh truoc")))
                .isEqualTo(new GatewayResult.Refused(RefusalReason.OUTPUT_HONEYTOKEN));
        assertThat(published).extracting(event -> {
            EgressDecisionEvent e = (EgressDecisionEvent) event;
            return e.direction() + ":" + e.decision() + ":" + e.attempt();
        }).containsExactly("REQUEST:PASS:1", "REQUEST:PASS:2", "RESPONSE:BLOCK:2");
    }

    /** SEC03-F3-AUDIT (A-59) review: a screen that throws, or names a value that does not exist, is a SCREEN_FAILED row. */
    @Test
    void aBrokenScreenIsRecordedAsAScreenFailure() {
        ResponseScreen throwing = values -> {
            throw new IllegalStateException("screen down");
        };
        ResponseScreen outOfRange = values -> Optional.of(new ResponseScreen.Hit("HT-NAME-A", values.size()));
        for (ResponseScreen broken : List.of(throwing, outOfRange)) {
            published.clear();
            provider.replies.add(new ProviderReply(200, OK_RESPONSE));

            assertThat(new DefaultAiGateway(policy, broken, provider, publisher, clock, killSwitch)
                    .complete(EgressFixtures.request("Toi can ma phanh truoc")))
                    .isEqualTo(new GatewayResult.Refused(RefusalReason.OUTPUT_HONEYTOKEN));
            EgressDecisionEvent withheld = (EgressDecisionEvent) published.get(1);
            assertThat(withheld.direction()).isEqualTo(EgressDecisionEvent.Direction.RESPONSE);
            assertThat(withheld.policyCode()).isEqualTo("SCREEN_FAILED");
            assertThat(withheld.honeytokenId()).isNull();
            assertThat(withheld.blockedFieldPath()).isEqualTo("$");
        }
    }

    /** SEC03-F3-AUDIT (A-59, 3A): the answer stays withheld when its record cannot be written. */
    @Test
    void anAnswerIsWithheldEvenWhenItsRecordFails() {
        provider.replies.add(new ProviderReply(200, "{\"text\":\"Khach ZZHoney CustomerA\"}".getBytes(StandardCharsets.UTF_8)));
        EgressAuditLog requestsOnly = event -> {
            if (event.direction() == EgressDecisionEvent.Direction.RESPONSE) {
                throw new IllegalStateException("audit down");
            }
            published.add(event);
        };

        assertThat(new DefaultAiGateway(policy, screen, provider, requestsOnly, clock, killSwitch)
                .complete(EgressFixtures.request("Toi can ma phanh truoc")))
                .isEqualTo(new GatewayResult.Refused(RefusalReason.OUTPUT_HONEYTOKEN));
        assertThat(published).hasSize(1);
    }

    /** SEC03-F3 (A-58): an ordinary answer and ordinary tool arguments pass the screen unchanged. */
    @Test
    void anOrdinaryAnswerPassesTheScreen() {
        provider.replies.add(new ProviderReply(200, ("{\"text\":\"Ma 04465-0K290 con hang, khach hang can bao gia khong?\","
                + "\"toolProposals\":[{\"name\":\"searchVerifiedProduct\",\"argumentsJson\":\"{\\\"oe\\\":\\\"04465-0K290\\\"}\"}]}")
                .getBytes(StandardCharsets.UTF_8)));

        assertThat(gateway().complete(EgressFixtures.request("Toi can ma phanh truoc")))
                .isInstanceOf(GatewayResult.Completed.class);
    }

    /**
     * SEC03-F3 (A-58) review: numbered steps, years, quantities and OE codes in an answer do not form a digit
     * sentinel (no all-digits or spelled projection for answers); a sentinel written with separators still does.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "{\"text\":\"Buoc 1. kiem tra ma\\nBuoc 2. xem xe\\nBuoc 3. a\\nBuoc 4. b\\nBuoc 5. c\\nBuoc 6. d\\nBuoc 7. e\\nBuoc 8. f\\nBuoc 9. g\"}",
        "{\"text\":\"Buoc chin, tam, bay, sau, nam, bon, ba, hai, mot\"}",
        "{\"text\":\"Xe doi 2019, can 2 bo, ma 12345-67890 va 04465-0K290\"}"
    })
    void numberedStepsAndCodesAreNotASentinel(String answer) {
        provider.replies.add(new ProviderReply(200, answer.getBytes(StandardCharsets.UTF_8)));

        assertThat(gateway().complete(EgressFixtures.request("Toi can ma phanh truoc")))
                .isInstanceOf(GatewayResult.Completed.class);
    }

    @Test
    void aSentinelWithSeparatorsInAnAnswerIsStillWithheld() {
        provider.replies.add(new ProviderReply(200, "{\"text\":\"Gia von 987.654.321\"}".getBytes(StandardCharsets.UTF_8)));

        assertThat(gateway().complete(EgressFixtures.request("Toi can ma phanh truoc")))
                .isEqualTo(new GatewayResult.Refused(RefusalReason.OUTPUT_HONEYTOKEN));
    }

    /** SEC03-F3 (A-58): a screen that fails withholds the answer (fail closed). */
    @Test
    void aFailingScreenWithholdsTheAnswer() {
        provider.replies.add(new ProviderReply(200, OK_RESPONSE));
        ResponseScreen broken = values -> {
            throw new IllegalStateException("screen down");
        };

        assertThat(new DefaultAiGateway(policy, broken, provider, publisher, clock, killSwitch)
                .complete(EgressFixtures.request("Toi can ma phanh truoc")))
                .isEqualTo(new GatewayResult.Refused(RefusalReason.OUTPUT_HONEYTOKEN));
    }

    /** The five sources named by T-SEC01-VARIANTS, each placing the variant in a different field. */
    static final Map<String, Function<String, ModelRequest>> SOURCES = Map.of(
            "user", v -> EgressFixtures.request("cho toi hoi " + v),
            "toolResult", v -> EgressFixtures.request(List.of(customer("tim ma phanh")), EgressFixtures.context(),
                    List.of(new ProductSearchResult(List.of(new ProductRef("pr_SynthProd01AAAAAAAAAAA", "SKU-BRK-PAD-A",
                            null, "Ma phanh " + v, Provenance.VERIFIED, AvailabilityBucket.CON_HANG))))),
            "catalog", v -> EgressFixtures.request(List.of(customer("tim ma phanh")),
                    new TechnicalContext(null, null, "Vios " + v, "Ma phanh", 1, List.of(), null), List.of()),
            "error", v -> EgressFixtures.request(EgressFixtures.TRACE_ID, List.of(customer("tim ma phanh"),
                    new SafeMessage(SafeMessage.Role.CONTEXT_SUMMARY, "Loi tra cuu: " + v))),
            "history", v -> EgressFixtures.request(EgressFixtures.TRACE_ID, List.of(
                    new SafeMessage(SafeMessage.Role.CONTEXT_SUMMARY, "Luot truoc khach nhac " + v),
                    customer("con hang khong"))));

    static Stream<Arguments> variantsBySource() {
        return SOURCES.keySet().stream().sorted().flatMap(source -> SentinelVariants.of(SentinelVariants.COST)
                .entrySet().stream().map(v -> Arguments.of(source, v.getKey(), v.getValue())));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("variantsBySource")
    void tSec01VariantIsBlockedAndNothingIsSent(String source, String variantId, String variant) {
        ModelRequest request;
        try {
            request = SOURCES.get(source).apply(variant);
        } catch (IllegalArgumentException typeGate) {
            // Layer 1: the egress type rejects the value (e.g. digits in a category label).
            assertThat(source).as(variantId).isEqualTo("toolResult");
            assertThat(provider.sent).isEmpty();
            return;
        }
        GatewayResult result = gateway().complete(request);

        assertThat(result).as(variantId).isEqualTo(new GatewayResult.Refused(RefusalReason.DLP_EGRESS));
        assertThat(provider.sent).as("bytes sent").isEmpty();
        EgressDecisionEvent event = singleEvent();
        assertThat(event.decision()).isEqualTo(EgressDecisionEvent.Decision.BLOCK);
        assertThat(event.policyCode()).isEqualTo("HONEYTOKEN");
        assertThat(event.honeytokenId()).isEqualTo("HT-COST-01");
    }

    @Test
    void spelledSentinelInCategoryPassesTheTypeGateButIsBlockedByTheScan() {
        String spelled = SentinelVariants.of(SentinelVariants.COST).get("V22-spelled-vi");
        assertThat(gateway().complete(SOURCES.get("toolResult").apply(spelled)))
                .isEqualTo(new GatewayResult.Refused(RefusalReason.DLP_EGRESS));
        assertThat(provider.sent).isEmpty();
    }

    /** Security review M5(d): TEXT honeytokens and the phone sentinel, through every source. */
    static Stream<Arguments> otherSentinelsBySource() {
        Map<String, String> tokens = Map.of(
                "HT-NAME-A", "ZZHoney CustomerA",
                "HT-EMAIL-A", "honey.customer.a@example.invalid",
                "HT-KEY-01", "APASHT_KEY_9f2c",
                "HT-PHONE-A", "0399 000 111");
        return SOURCES.keySet().stream().sorted().flatMap(source -> tokens.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(t -> Arguments.of(source, t.getKey(), t.getValue())));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("otherSentinelsBySource")
    void textAndPhoneSentinelsAreBlockedInEverySource(String source, String honeytokenId, String token) {
        ModelRequest request;
        try {
            request = SOURCES.get(source).apply(token);
        } catch (IllegalArgumentException typeGate) {
            assertThat(source).isEqualTo("toolResult");
            return;
        }
        assertThat(gateway().complete(request)).isEqualTo(new GatewayResult.Refused(RefusalReason.DLP_EGRESS));
        assertThat(provider.sent).isEmpty();
        assertThat(singleEvent().honeytokenId()).isEqualTo(honeytokenId);
    }

    /** Mutation control (security review M5(e)): with a pass-all policy the oracle must catch the leak. */
    @Test
    void oracleDetectsLeakWhenPolicyIsDisabled() {
        DefaultAiGateway unguarded = new DefaultAiGateway(payload -> EgressVerdict.pass(), screen,
                provider, publisher, clock, killSwitch);
        String variant = SentinelVariants.of(SentinelVariants.COST).get("V02-dots");

        unguarded.complete(EgressFixtures.request("gia von " + variant));

        assertThat(provider.sent).singleElement().satisfies(body -> {
            assertThat(EgressScan.containsSentinel(body)).isTrue();
            assertThat(EgressScan.containsExact(body, variant)).isTrue();
        });
    }

    /** False-positive corpus from security review M3: ordinary auto-parts traffic must pass. */
    @Test
    void ordinaryAutoPartsTrafficPasses() {
        ModelRequest request = EgressFixtures.request(List.of(
                        customer("Xe gia dinh, doi 2019, can thay ma phanh"),
                        customer("Vios 2019 dong co 1.5 dung loc gio nao")),
                new TechnicalContext("JTD**************", "vin_ABCDEFGHJKLMN1234AAAAA", "Vios 2019 dong co 1.5",
                        "Ma phanh", 2, List.of(EgressFixtures.product()), "qh_ABCDEFGHJKLMN1234-ABCD"),
                EgressFixtures.toolResults());

        assertThat(gateway().complete(request)).isInstanceOf(GatewayResult.Completed.class);
        assertThat(provider.sent).hasSize(1);
    }

    @Test
    void cancelledSendIsNotRetried() {
        provider.failures.add(ProviderTransportException.Kind.CANCELLED);
        assertThat(gateway().complete(EgressFixtures.request("xin chao")))
                .isEqualTo(new GatewayResult.Failed(FailureReason.PROVIDER_UNAVAILABLE));
        assertThat(provider.sent).hasSize(1);
    }

    @Test
    void unexpectedTransportExceptionBecomesAReasonCode(CapturedOutput output) {
        ModelProviderPort throwing = (body, budget) -> {
            throw new IllegalStateException("boom " + SentinelVariants.COST);
        };
        DefaultAiGateway gateway = new DefaultAiGateway(policy, screen, throwing, publisher, clock, killSwitch);

        assertThat(gateway.complete(EgressFixtures.request("xin chao")))
                .isEqualTo(new GatewayResult.Failed(FailureReason.PROVIDER_UNAVAILABLE));
        assertThat(output.getAll()).contains("exception=IllegalStateException").doesNotContain(SentinelVariants.COST);
    }

    @Test
    void sentinelSplitAcrossMessagesIsBlocked() {
        ModelRequest request = EgressFixtures.request(EgressFixtures.TRACE_ID,
                List.of(customer("ma so 987654"), customer("tiep theo la 321")));
        assertThat(gateway().complete(request)).isEqualTo(new GatewayResult.Refused(RefusalReason.DLP_EGRESS));
        assertThat(provider.sent).isEmpty();
        assertThat(singleEvent().blockedFieldPath()).isEqualTo("$");
    }

    @Test
    void blockReportsFieldPathButNoValue(CapturedOutput output) {
        gateway().complete(EgressFixtures.request("gia von " + SentinelVariants.COST));

        EgressDecisionEvent event = singleEvent();
        assertThat(event.blockedFieldPath()).isEqualTo("$.messages[0].text");
        assertThat(event.toString()).doesNotContain(SentinelVariants.COST).doesNotContain("gia von");
        assertThat(output.getAll()).contains("decision=BLOCK policy=HONEYTOKEN field=$.messages[0].text")
                .doesNotContain(SentinelVariants.COST);
    }

    @Test
    void auditFailureMeansNothingIsSent() {
        EgressAuditLog failing = event -> {
            throw new IllegalStateException("db down");
        };
        DefaultAiGateway gateway = new DefaultAiGateway(policy, screen, provider, failing, clock, killSwitch);
        provider.replies.add(new ProviderReply(200, OK_RESPONSE));

        assertThat(gateway.complete(EgressFixtures.request("xin chao")))
                .isEqualTo(new GatewayResult.Refused(RefusalReason.AUDIT_UNAVAILABLE));
        assertThat(provider.sent).isEmpty();
    }

    @Test
    void policyExceptionFailsClosed() {
        EgressPolicy broken = payload -> {
            throw new IllegalStateException("scanner bug");
        };
        DefaultAiGateway gateway = new DefaultAiGateway(broken, screen, provider, publisher, clock, killSwitch);

        assertThat(gateway.complete(EgressFixtures.request("xin chao")))
                .isEqualTo(new GatewayResult.Refused(RefusalReason.DLP_EGRESS));
        assertThat(provider.sent).isEmpty();
        assertThat(singleEvent().policyCode()).isEqualTo("MALFORMED_PAYLOAD");
    }

    @Test
    void serverErrorIsRetriedOnceWithIdenticalBytes() {
        provider.replies.add(new ProviderReply(503, new byte[0]));
        provider.replies.add(new ProviderReply(200, OK_RESPONSE));

        assertThat(gateway().complete(EgressFixtures.request("xin chao"))).isInstanceOf(GatewayResult.Completed.class);
        assertThat(provider.sent).hasSize(2);
        assertThat(provider.sent.get(1)).isEqualTo(provider.sent.get(0));
        // Second review M3/L3: one PASS record per attempt, written before that attempt is sent.
        assertThat(published).hasSize(2).allSatisfy(e -> assertThat(((EgressDecisionEvent) e).decision())
                .isEqualTo(EgressDecisionEvent.Decision.PASS));
        assertThat(published).extracting(e -> ((EgressDecisionEvent) e).attempt()).containsExactly(1, 2);
    }

    @Test
    void retryIsNotSentWhenItsAuditFails() {
        List<Object> recorded = new ArrayList<>();
        EgressAuditLog failsOnSecond = event -> {
            if (!recorded.isEmpty()) {
                throw new IllegalStateException("db down");
            }
            recorded.add(event);
        };
        provider.replies.add(new ProviderReply(503, new byte[0]));
        provider.replies.add(new ProviderReply(200, OK_RESPONSE));

        assertThat(new DefaultAiGateway(policy, screen, provider, failsOnSecond, clock, killSwitch).complete(EgressFixtures.request("xin chao")))
                .isEqualTo(new GatewayResult.Refused(RefusalReason.AUDIT_UNAVAILABLE));
        assertThat(provider.sent).hasSize(1);
    }

    @Test
    void blockIsRecordedWithAttemptZero() {
        gateway().complete(EgressFixtures.request("gia von " + SentinelVariants.COST));
        assertThat(singleEvent().attempt()).isZero();
    }

    /** Second review M1/L1: without an audit log the gateway cannot be built (no silent send). */
    @Test
    void gatewayCannotBeBuiltWithoutAnAuditLog() {
        assertThatThrownBy(() -> new DefaultAiGateway(policy, screen, provider, null, clock, killSwitch))
                .isInstanceOf(NullPointerException.class);
    }

    /** Second review M1/L1: a Spring context with the gateway but no audit log does not start. */
    @Test
    void contextWithoutAnAuditLogDoesNotStart() {
        new ApplicationContextRunner()
                .withBean(EgressPolicy.class, () -> policy)
                .withBean(ResponseScreen.class, () -> screen)
                .withBean(ModelProviderPort.class, () -> provider)
                .withBean(Clock.class, () -> clock)
                .withBean(KillSwitch.class, () -> killSwitch)
                .withBean(DefaultAiGateway.class)
                .run(context -> assertThat(context).hasFailed());
        new ApplicationContextRunner()
                .withBean(EgressPolicy.class, () -> policy)
                .withBean(ResponseScreen.class, () -> screen)
                .withBean(ModelProviderPort.class, () -> provider)
                .withBean(Clock.class, () -> clock)
                .withBean(KillSwitch.class, () -> killSwitch)
                .withBean(EgressAuditLog.class, () -> publisher)
                .withBean(DefaultAiGateway.class)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void retriesAreBounded() {
        provider.replies.add(new ProviderReply(503, new byte[0]));
        provider.replies.add(new ProviderReply(502, new byte[0]));
        provider.replies.add(new ProviderReply(200, OK_RESPONSE));

        assertThat(gateway().complete(EgressFixtures.request("xin chao")))
                .isEqualTo(new GatewayResult.Failed(FailureReason.PROVIDER_UNAVAILABLE));
        assertThat(provider.sent).hasSize(DefaultAiGateway.MAX_ATTEMPTS);
    }

    @Test
    void timeoutIsRetriedThenFails() {
        provider.failures.add(ProviderTransportException.Kind.TIMEOUT);
        provider.failures.add(ProviderTransportException.Kind.TIMEOUT);

        assertThat(gateway().complete(EgressFixtures.request("xin chao")))
                .isEqualTo(new GatewayResult.Failed(FailureReason.PROVIDER_UNAVAILABLE));
        assertThat(provider.sent).hasSize(2);
    }

    @Test
    void revokedKeyIsNotRetried() {
        provider.replies.add(new ProviderReply(401, new byte[0]));

        assertThat(gateway().complete(EgressFixtures.request("xin chao")))
                .isEqualTo(new GatewayResult.Failed(FailureReason.PROVIDER_AUTH));
        assertThat(provider.sent).hasSize(1);
    }

    /** T-SEC07 (EP-09a, decision 5A): a 401/403 opens the breaker; nothing is sent until an operator acts. */
    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void aRevokedKeyOpensTheBreakerAndStopsEveryLaterCall(int status) {
        provider.replies.add(new ProviderReply(status, new byte[0]));
        DefaultAiGateway gateway = gateway();

        assertThat(gateway.complete(EgressFixtures.request("xin chao")))
                .isEqualTo(new GatewayResult.Failed(FailureReason.PROVIDER_AUTH));
        assertThat(killSwitch.trips).hasValue(1);
        int audited = published.size();

        assertThat(gateway.complete(EgressFixtures.request("xin chao lan nua")))
                .isEqualTo(new GatewayResult.Refused(RefusalReason.PROVIDER_AUTH_BREAKER));
        assertThat(provider.sent).hasSize(1);
        assertThat(published).as("nothing audited as sent").hasSize(audited);

        killSwitch.set(true, "usr_AAAAAAAAAAAAAAAAAAAAAA");
        provider.replies.add(new ProviderReply(200, OK_RESPONSE));
        assertThat(gateway.complete(EgressFixtures.request("xin chao"))).isInstanceOf(GatewayResult.Completed.class);
    }

    /** T-SEC06 (ADR-013): switched off or unreadable → nothing serialized, audited or sent. */
    @Test
    void aSwitchedOffOrUnreadableKillSwitchSendsNothing() {
        killSwitch.set(false, "usr_AAAAAAAAAAAAAAAAAAAAAA");
        assertThat(gateway().complete(EgressFixtures.request("xin chao")))
                .isEqualTo(new GatewayResult.Refused(RefusalReason.AI_DISABLED));

        killSwitch.set(true, "usr_AAAAAAAAAAAAAAAAAAAAAA");
        killSwitch.unreadable(true);
        assertThat(gateway().complete(EgressFixtures.request("xin chao")))
                .isEqualTo(new GatewayResult.Refused(RefusalReason.AI_DISABLED));

        assertThat(provider.sent).isEmpty();
        assertThat(published).isEmpty();
    }

    /** Review round 2 (architecture M-A): a switch that throws is off, and a breaker that cannot be stored
     * does not change the 401 outcome. */
    @Test
    void aThrowingSwitchIsOffAndAFailingTripKeepsTheAuthOutcome() {
        killSwitch.throwOnRead = true;
        assertThat(gateway().complete(EgressFixtures.request("xin chao")))
                .isEqualTo(new GatewayResult.Refused(RefusalReason.AI_DISABLED));
        assertThat(provider.sent).isEmpty();
        assertThat(published).isEmpty();

        killSwitch.throwOnRead = false;
        KillSwitch failingTrip = new TestKillSwitch() {
            @Override
            public void tripProviderAuth() {
                throw new IllegalStateException("store down");
            }
        };
        provider.replies.add(new ProviderReply(401, new byte[0]));
        assertThat(new DefaultAiGateway(policy, screen, provider, publisher, clock, failingTrip)
                .complete(EgressFixtures.request("xin chao")))
                .isEqualTo(new GatewayResult.Failed(FailureReason.PROVIDER_AUTH));
    }

    /** ADR-013: checked before every provider call: a switch turned off after a 5xx stops the retry. */
    @Test
    void aSwitchTurnedOffBetweenAttemptsStopsTheRetry() {
        provider.replies.add(new ProviderReply(503, new byte[0]));
        provider.replies.add(new ProviderReply(200, OK_RESPONSE));
        killSwitch.offAfterReads = 2; // the entry check and the first attempt see it on

        assertThat(gateway().complete(EgressFixtures.request("xin chao")))
                .isEqualTo(new GatewayResult.Refused(RefusalReason.AI_DISABLED));
        assertThat(provider.sent).hasSize(1);
    }

    @Test
    void unconfiguredProviderFailsClosedWithoutRetry() {
        provider.failures.add(ProviderTransportException.Kind.NOT_CONFIGURED);

        assertThat(gateway().complete(EgressFixtures.request("xin chao")))
                .isEqualTo(new GatewayResult.Failed(FailureReason.PROVIDER_UNAVAILABLE));
        assertThat(provider.sent).hasSize(1);
    }

    static Stream<Arguments> invalidResponses() {
        return Stream.of(
                Arguments.of("unknown property", "{\"text\":\"ok\",\"approved\":true}"),
                Arguments.of("duplicate key", "{\"text\":\"ok\",\"text\":\"approved\"}"),
                Arguments.of("trailing tokens", "{\"text\":\"ok\"} {\"text\":\"x\"}"),
                Arguments.of("empty", ""),
                Arguments.of("not json", "approved order 987654321"),
                Arguments.of("schema violation", "{\"toolProposals\":[{\"name\":\"../approve\",\"argumentsJson\":\"{}\"}]}"),
                Arguments.of("oversized", "{\"text\":\"" + "a".repeat(EgressSerializer.MAX_RESPONSE_BYTES) + "\"}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidResponses")
    void invalidProviderResponseIsRejectedWithoutLoggingIt(String name, String body, CapturedOutput output) {
        provider.replies.add(new ProviderReply(200, body.getBytes(StandardCharsets.UTF_8)));

        assertThat(gateway().complete(EgressFixtures.request("xin chao")))
                .isEqualTo(new GatewayResult.Failed(FailureReason.INVALID_PROVIDER_RESPONSE));
        assertThat(output.getAll()).doesNotContain("approved").doesNotContain(SentinelVariants.COST);
    }

    private EgressDecisionEvent singleEvent() {
        assertThat(published).hasSize(1);
        return (EgressDecisionEvent) published.get(0);
    }

    private static SafeMessage customer(String text) {
        return new SafeMessage(SafeMessage.Role.CUSTOMER, text);
    }

    /** Records exact bytes per attempt; replies or fails from queues (default 200 OK). */
    static final class RecordingProvider implements ModelProviderPort {

        final List<byte[]> sent = new ArrayList<>();
        final Deque<ProviderReply> replies = new ArrayDeque<>();
        final Deque<ProviderTransportException.Kind> failures = new ArrayDeque<>();

        @Override
        public ProviderReply send(byte[] requestBody, ResponseBudget budget) throws ProviderTransportException {
            sent.add(requestBody.clone());
            if (!failures.isEmpty()) {
                throw new ProviderTransportException(failures.poll());
            }
            return replies.isEmpty() ? new ProviderReply(200, OK_RESPONSE) : replies.poll();
        }
    }

    /** Third review L-1/L-3: a long repetitive or encoded text is refused or sent, never unaudited. */
    @Test
    void longRepetitiveTextIsAlwaysAudited() {
        provider.replies.add(new ProviderReply(200, OK_RESPONSE));
        provider.replies.add(new ProviderReply(200, OK_RESPONSE));
        for (String unit : List.of("AB ", "AB-")) {
            published.clear();
            GatewayResult result = gateway().complete(EgressFixtures.request(unit.repeat(SafeMessage.MAX_TEXT_CHARS / 3)));
            assertThat(result).isNotNull();
            assertThat(published).isNotEmpty();
        }
    }
}
