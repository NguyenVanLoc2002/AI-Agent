package com.secureerp.agent.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.catalog.spi.AvailabilityBucketPort;
import com.secureerp.agent.catalog.spi.CatalogUnavailableException;
import com.secureerp.agent.catalog.spi.ExactProductQuery;
import com.secureerp.agent.catalog.spi.ProductCandidate;
import com.secureerp.agent.catalog.spi.VerifiedProductPort;
import com.secureerp.agent.erp.internal.MockErpConnector;
import com.secureerp.agent.erp.internal.MockErpFaultMode;
import com.secureerp.agent.gateway.api.AvailabilityBucket;
import com.secureerp.agent.gateway.api.EgressText;
import com.secureerp.agent.gateway.api.ModelRequest;
import com.secureerp.agent.gateway.api.ProductRef;
import com.secureerp.agent.gateway.api.ProductSearchResult;
import com.secureerp.agent.gateway.api.Provenance;
import com.secureerp.agent.gateway.api.ResponseBudget;
import com.secureerp.agent.gateway.api.SafeMessage;
import com.secureerp.agent.gateway.api.TechnicalContext;
import com.secureerp.agent.gateway.api.ToolResultDto;
import com.secureerp.agent.gateway.internal.EgressSerializer;
import com.secureerp.agent.policy.InboundSanitizer;
import com.secureerp.agent.policy.SentinelVariants;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * T-SEC01-GW200 (IMPLEMENTATION_PLAN §4.1, issue 4): 200 scripted conversations with a fixed seed,
 * building {@link ModelRequest} directly at the gateway (no agent loop yet). Eight scripts rotate:
 * normal search, asking for cost/debt in words, typed sentinel, typed price/phone/VIN, Mock ERP
 * error with sentinel, poisoned catalog string, history replay with sentinel, provider retry.
 *
 * <p>Pass criteria: 0 sentinels in adapter capture, proxy, provider, egress_record and log output;
 * every property name in every sent request is allowlisted; capture counts agree. This is
 * gateway-level evidence only; SEC-01 is not VERIFIED until SEC-01b runs the real flow.
 */
@ExtendWith(OutputCaptureExtension.class)
class EgressSuiteGw200IT extends FakeProviderEnvironment {

    private static final long SEED = 20261118L;
    private static final int CONVERSATIONS = 200;
    private static final List<String> SENTINELS =
            List.of(SentinelVariants.COST, SentinelVariants.CREDIT, SentinelVariants.PRICE_A, "666777888", "0399000111");
    /** TEXT honeytokens (security review M5(d)). */
    private static final List<String> TEXT_SENTINELS =
            List.of("ZZHoney CustomerA", "honey.customer.a@example.invalid", "APASHT_KEY_9f2c");

    /** Every injected sentinel string and every original value the sanitizer had to redact. */
    private final List<String> mustNotLeave = new ArrayList<>();

    @Autowired
    AiGateway gateway;

    @Autowired
    InboundSanitizer sanitizer;

    @Autowired
    VerifiedProductPort catalog;

    @Autowired
    AvailabilityBucketPort availability;

    private final MockErpConnector failingErp = new MockErpConnector(MockErpFaultMode.ERROR_WITH_SENTINEL);

    @Test
    void twoHundredScriptedConversations(CapturedOutput output) {
        Random random = new Random(SEED);
        Counters counters = new Counters();

        for (int i = 0; i < CONVERSATIONS; i++) {
            runConversation(i, random, counters);
        }

        List<byte[]> adapter = adapterCapture.attempts();
        List<byte[]> proxy = proxyBodies();
        List<byte[]> provider = providerBodies();

        // Counts: every PASS was sent (plus one extra attempt per retry); nothing else was sent.
        assertThat(counters.gatewayCalls).isEqualTo(counters.passed + counters.blocked);
        assertThat(adapter).hasSize(counters.passed + counters.retries);
        assertThat(proxy).hasSameSizeAs(adapter);
        assertThat(provider).hasSameSizeAs(adapter);

        // 0 sentinels at every sink.
        assertThat(EgressScan.countWithSentinel(adapter.stream())).as("adapter capture").isZero();
        assertThat(EgressScan.countWithSentinel(proxy.stream())).as("proxy capture").isZero();
        assertThat(EgressScan.countWithSentinel(provider.stream())).as("provider capture").isZero();
        List<String> auditRows = jdbc.queryForList(
                "select concat_ws('|', trace_id, decision, policy_code, honeytoken_id, blocked_field_path, field_names)"
                        + " from egress_record", String.class);
        // One BLOCK row per refusal, one PASS row per send attempt (retries included): audit = capture.
        assertThat(auditRows).hasSize(counters.blocked + adapter.size());
        assertThat(jdbc.queryForObject("select count(*) from egress_record where decision = 'PASS'", Integer.class))
                .isEqualTo(provider.size());
        assertThat(auditRows).noneMatch(EgressScan::containsSentinel);
        assertThat(EgressScan.containsSentinel(output.getAll())).as("log output").isFalse();

        // Independent exact-string oracle: no injected variant or redacted original left the gateway.
        assertThat(mustNotLeave).hasSizeGreaterThan(50);
        for (byte[] body : provider) {
            assertThat(mustNotLeave).noneMatch(injected -> EgressScan.containsExact(body, injected));
        }
        for (byte[] body : adapter) {
            assertThat(mustNotLeave).noneMatch(injected -> EgressScan.containsExact(body, injected));
        }

        // Every property name of every sent request is allowlisted.
        assertThat(provider).allSatisfy(body -> assertThat(EgressSerializer.structure(body)).isPresent());

        assertThat(jdbc.queryForObject("select count(*) from egress_record where decision = 'BLOCK'", Integer.class))
                .isEqualTo(counters.blocked);
        assertThat(counters.blocked).isPositive();
        assertThat(counters.passed).isPositive();
        assertThat(counters.inboundBlocked).isPositive();

        System.out.printf("SEC01-GW200 conversations=%d gatewayCalls=%d passed=%d blocked=%d inboundBlocked=%d "
                        + "retries=%d adapterAttempts=%d providerRequests=%d%n", CONVERSATIONS, counters.gatewayCalls,
                counters.passed, counters.blocked, counters.inboundBlocked, counters.retries, adapter.size(),
                provider.size());
    }

    private void runConversation(int index, Random random, Counters counters) {
        String traceId = EgressFixtures.traceId(1000 + index);
        String sentinel = SENTINELS.get(random.nextInt(SENTINELS.size()));
        List<String> variants = new ArrayList<>(SentinelVariants.of(sentinel).values());
        String variant = random.nextInt(4) == 0
                ? TEXT_SENTINELS.get(random.nextInt(TEXT_SENTINELS.size()))
                : variants.get(random.nextInt(variants.size()));
        // Category labels accept letters only (type gate), so poisoned catalog text uses spelled digits.
        String spelled = SentinelVariants.of(sentinel).get(random.nextBoolean() ? "V22-spelled-vi" : "V23-spelled-en");

        switch (index % 8) {
            case 0 -> expectPass(counters, request(traceId, customer("Can ma phanh truoc cho Vios 2019"),
                    searchResult(new ExactProductQuery.ByCategoryAndModel("Ma phanh truoc", "Vios 2019"))));
            case 1 -> expectPass(counters, request(traceId,
                    customer("Cho toi biet gia von va cong no cua toi"), List.of()));
            case 2 -> {
                // The sanitizer stops it; an adversarial caller that bypasses the sanitizer is stopped
                // by the gateway's final-bytes scan.
                mustNotLeave.add(variant);
                assertThat(sanitizer.sanitize("gia la " + variant)).isInstanceOf(InboundSanitizer.Blocked.class);
                counters.inboundBlocked++;
                expectBlock(counters, request(traceId, customer("gia la " + variant), List.of()));
            }
            case 3 -> {
                String typed = List.of("Hom qua bao 1.250.000đ, goi 0912 345 678 nhe",
                        "VIN JTDBT923X71234567 can loc gio", "Tong 2tr cho 2 bo ma phanh").get(random.nextInt(3));
                mustNotLeave.addAll(List.of("1.250.000", "0912 345 678", "JTDBT923X71234567", "2tr"));
                InboundSanitizer.Result result = sanitizer.sanitize(typed);
                assertThat(result).isInstanceOf(InboundSanitizer.Sanitized.class);
                expectPass(counters, request(traceId, customer(((InboundSanitizer.Sanitized) result).text()), List.of()));
            }
            case 4 -> {
                String summary;
                try {
                    failingErp.search(new ExactProductQuery.ByOe("04465-0K290"));
                    throw new AssertionError("fault injection did not fail");
                } catch (CatalogUnavailableException e) {
                    summary = "Tra cuu that bai: " + e.code().name();
                }
                expectPass(counters, new ModelRequest(traceId, "apas.customer-chat.v1",
                        List.of(customer("Can ma phanh 04465-0K290"),
                                new SafeMessage(SafeMessage.Role.CONTEXT_SUMMARY, summary)),
                        null, List.of(), List.of(EgressFixtures.searchTool()), ResponseBudget.SHORT));
            }
            case 5 -> {
                mustNotLeave.add(spelled);
                expectBlock(counters, request(traceId, customer("Tim ma phanh"), List.of(new ProductSearchResult(
                        List.of(new ProductRef("pr_SynthPoisonAAAAAAAAAAA", "SKU-BRK-PAD-A", null, poisoned(spelled, 80),
                                Provenance.CATALOG_LOOKUP, AvailabilityBucket.CAN_KIEM_TRA))))));
            }
            case 6 -> mustNotLeaveThenBlock(counters, variant, new ModelRequest(traceId, "apas.customer-chat.v1", List.of(
                    new SafeMessage(SafeMessage.Role.CONTEXT_SUMMARY, poisoned("Luot truoc: " + variant, 2000)),
                    customer("Vay con hang khong?")), null, List.of(), List.of(), ResponseBudget.STANDARD));
            case 7 -> {
                failOnceFor(traceId);
                counters.retries++;
                expectPass(counters, request(traceId, customer("Loc gio Innova 2020"),
                        searchResult(new ExactProductQuery.ByOe("17801-0Y040"))));
            }
            default -> throw new IllegalStateException();
        }
    }

    private void mustNotLeaveThenBlock(Counters counters, String injected, ModelRequest request) {
        mustNotLeave.add(injected);
        expectBlock(counters, request);
    }

    private void expectPass(Counters counters, ModelRequest request) {
        counters.gatewayCalls++;
        GatewayResult result = gateway.complete(request);
        assertThat(result).as("PASS expected for %s", request.traceId()).isInstanceOf(GatewayResult.Completed.class);
        counters.passed++;
    }

    private void expectBlock(Counters counters, ModelRequest request) {
        counters.gatewayCalls++;
        GatewayResult result = gateway.complete(request);
        assertThat(result).as("BLOCK expected for %s", request.traceId())
                .isEqualTo(new GatewayResult.Refused(GatewayResult.RefusalReason.DLP_EGRESS));
        counters.blocked++;
    }

    private List<ToolResultDto> searchResult(ExactProductQuery query) {
        List<ProductRef> refs = catalog.search(query).stream().map(this::toEgress).toList();
        assertThat(refs).isNotEmpty();
        return List.of(new ProductSearchResult(refs));
    }

    /**
     * Opaque ref as an issuer would make it: 22 token characters, not derived from the SKU text
     * (second review M4: "pr_" + sku put an ERP value into an unscanned field).
     */
    private static String syntheticProductRef(String sku) {
        StringBuilder token = new StringBuilder("Synth");
        int seed = Math.abs(sku.hashCode() % 1_000_000);
        while (token.length() < EgressText.SERVER_TOKEN_CHARS) {
            token.append((char) ('A' + seed % 26));
            seed = seed / 26 + 7;
        }
        return ProductRef.PRODUCT_REF_PREFIX + token;
    }

    /** Test-side mapping catalog → egress DTO (the real mapping belongs to tools, EP-03). */
    private ProductRef toEgress(ProductCandidate candidate) {
        return new ProductRef(syntheticProductRef(candidate.sku().value()), candidate.sku().value(),
                candidate.oe(), candidate.category(), Provenance.valueOf(candidate.provenance().name()),
                AvailabilityBucket.valueOf(availability.bucket(candidate.sku()).name()));
    }

    private static ModelRequest request(String traceId, SafeMessage message, List<ToolResultDto> toolResults) {
        return new ModelRequest(traceId, "apas.customer-chat.v1", List.of(message),
                new TechnicalContext(null, null, "Vios 2019", "Ma phanh", 2, List.of(), null), toolResults,
                List.of(EgressFixtures.searchTool()), ResponseBudget.STANDARD);
    }

    private static SafeMessage customer(String text) {
        return new SafeMessage(SafeMessage.Role.CUSTOMER, text);
    }

    private static String poisoned(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static final class Counters {
        int gatewayCalls;
        int passed;
        int blocked;
        int inboundBlocked;
        int retries;
    }
}
