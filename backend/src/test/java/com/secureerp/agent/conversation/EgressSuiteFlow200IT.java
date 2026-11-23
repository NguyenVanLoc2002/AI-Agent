package com.secureerp.agent.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.DemoHttpClient;
import com.secureerp.agent.agent.AgentTurn;
import com.secureerp.agent.gateway.EgressScan;
import com.secureerp.agent.policy.SentinelVariants;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T-SEC01-SUITE200 and T-SEC01-HISTORY with double capture (IMPLEMENTATION_PLAN §4, issue 10, SEC-01b):
 * 200 conversations with a fixed seed through the real flow (HTTP login and chat → conversation → agent →
 * tools → gateway → adapter → local proxy → Fake provider). Twelve scripts rotate: normal search, asking for
 * cost/debt in words, typed sentinel variant, typed price/phone/VIN replayed through history, ERP failure with
 * sentinels, ERP timeout, poisoned catalog row (category or OE), quote with the price sentinel followed by
 * three turns, provider retry, credit-blocked customer (NOT_QUOTED), price in the model answer, tool call with
 * an extra argument.
 *
 * <p>Pass criteria: 0 sentinels and 0 customer prices or typed values (seed values, also with separators
 * removed) in adapter, proxy and provider capture, in every table except {@code quote}/{@code quote_line}
 * (which legitimately hold prices) and in the log output; every property name of every sent request is in a
 * hard-coded set (independent of the production allowlist); the three captures and the PASS audit rows agree
 * in number; blocked and sent counts are reported.
 *
 * <p>Scope: Fake provider and local proxy only (ADR-004/016), not a real provider or network egress. The
 * clock jumps 61 minutes before each conversation, so per-hour limits do not end the suite (limits have their
 * own tests). Sentinel variants are sampled (cycled, not all 35 × 5); the full variant matrix is
 * T-SEC01-VARIANTS at gateway level.
 */
@ExtendWith(OutputCaptureExtension.class)
class EgressSuiteFlow200IT extends EgressFlowEnvironment {

    private static final long SEED = 20261119L;
    private static final int CONVERSATIONS = 200;
    private static final int SCRIPTS = 12;
    private static final String OE = "04465-0K290";
    private static final List<String> DIGIT_SENTINELS =
            List.of(SentinelVariants.COST, SentinelVariants.CREDIT, SentinelVariants.PRICE_A, "666777888", "0399000111");
    private static final List<String> TEXT_SENTINELS =
            List.of("ZZHoney CustomerA", "honey.customer.a@example.invalid", "APASHT_KEY_9f2c");
    /**
     * Seed digit values beyond {@link EgressScan}'s sentinels, matched after NFKC and separator removal (security
     * review M2): the quote total of cust-a and the typed price and phone of script 3.
     */
    private static final List<String> SEED_DIGITS = List.of("1110888666", "1250000", "0912345678");
    private static final Pattern DIGIT_SEPARATORS = Pattern.compile("(?<=\\d)[\\s.,_'’  ]+(?=\\d)");
    /**
     * Every property name a model request may carry, written out by hand from the {@code gateway.api} records
     * (security review M3): a new record component fails here until it is reviewed, even though the production
     * allowlist would accept it.
     */
    static final Set<String> EXPECTED_PROPERTY_NAMES = Set.of(
            "traceId", "systemPromptId", "messages", "context", "toolResults", "tools", "budget",
            "role", "text",
            "maskedVin", "vinRef", "vehicleModel", "partCategory", "requestedQuantity", "verifiedProducts", "quoteHandle",
            "productRef", "sku", "oe", "category", "provenance", "availabilityBucket",
            "candidates", "status", "lineCount", "code", "accepted",
            "name", "jsonSchema", "type");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> SCRIPT_3_TEXTS = List.of("Hôm qua báo 1.250.000đ, gọi 0912 345 678 nhé",
            "Xe tôi VIN 5ZZAPAS11RA000001 cần lọc gió", "Tổng 2tr cho 2 bộ má phanh");
    private static final List<String> SCRIPT_3_MARKERS = List.of("[SDT]", "5ZZ**************", "[SO_TIEN]");

    private final List<String> mustNotLeave = new ArrayList<>();
    private final List<String> variantPool = new ArrayList<>();
    private final Set<String> variantsUsed = new HashSet<>();
    private final Map<Outcome, Integer> outcomes = new EnumMap<>(Outcome.class);
    private int expectedModelCalls;
    private int expectedGatewayBlocks;
    private int retries;
    private int inboundCount;

    @Test
    void twoHundredConversationsThroughTheRealFlow(CapturedOutput output) {
        for (String sentinel : DIGIT_SENTINELS) {
            variantPool.addAll(SentinelVariants.of(sentinel).values());
        }
        variantPool.addAll(TEXT_SENTINELS);
        Random random = new Random(SEED);
        for (int i = 0; i < CONVERSATIONS; i++) {
            clock.advance(Duration.ofMinutes(61));
            runConversation(i, random);
        }

        List<byte[]> adapter = adapterCapture.attempts();
        List<byte[]> proxy = proxyBodies();
        List<byte[]> provider = providerRawBodies();

        // Counts: one adapter attempt per model call plus one per retry; the three captures agree, and every
        // attempt (the retry included) has its own PASS audit row.
        assertThat(provider).hasSize(expectedModelCalls + retries);
        assertThat(adapter).hasSameSizeAs(provider);
        assertThat(proxy).hasSameSizeAs(provider);
        assertThat(count("select count(*) from egress_record where decision = 'PASS'")).isEqualTo(provider.size());
        assertThat(count("select count(*) from egress_record where decision = 'BLOCK'")).isEqualTo(expectedGatewayBlocks);

        // 0 sentinels, seed prices or typed values at every egress capture.
        for (List<byte[]> capture : List.of(adapter, proxy, provider)) {
            assertThat(EgressScan.countWithSentinel(capture.stream())).isZero();
            for (byte[] body : capture) {
                String text = new String(body, StandardCharsets.UTF_8);
                assertNoSeedValue(text, "egress body");
                assertThat(NEVER_EGRESS).as("egress body").noneMatch(text::contains);
                assertThat(mustNotLeave).noneMatch(injected -> EgressScan.containsExact(body, injected));
            }
        }

        // Every table except quote/quote_line (which hold the rendered prices by design).
        List<String> tables = jdbc.queryForList("select table_name from information_schema.tables where table_schema ="
                + " 'public' and table_type = 'BASE TABLE' and table_name not in ('quote', 'quote_line',"
                + " 'flyway_schema_history')", String.class);
        assertThat(tables).contains("egress_record", "tool_invocation", "handoff", "message", "conversation",
                "quote_request", "product_ref", "conversation_vin", "customer_link", "principal_binding", "quote_handle");
        for (String table : tables) {
            for (String row : jdbc.queryForList("select row_to_json(t)::text from " + table + " t", String.class)) {
                assertNoSeedValue(row, table);
                assertThat(EgressScan.containsSentinel(row)).as(table).isFalse();
            }
        }

        // Logs: positive control first (the gateway's own log lines are in the capture), then 0 values.
        String logs = output.getAll();
        assertThat(logs).contains("gateway.egress decision=PASS").contains("gateway.egress decision=BLOCK");
        assertThat(EgressScan.containsSentinel(logs)).as("log output").isFalse();
        // No separator collapse on logs: real wall-clock timestamps would join into digit runs by chance.
        assertThat(SEED_DIGITS).noneMatch(logs::contains);
        assertThat(logs).doesNotContain("1.110.888.666").doesNotContain("1.250.000").doesNotContain("0912 345 678");
        assertThat(mustNotLeave).noneMatch(logs::contains);

        // Property names against the hand-written set (independent of EgressSerializer's allowlist).
        Set<String> seen = new TreeSet<>();
        provider.forEach(body -> collectPropertyNames(body, seen));
        assertThat(EXPECTED_PROPERTY_NAMES).containsAll(seen);

        // The suite exercised every path.
        assertThat(outcomes.keySet()).containsAll(List.of(Outcome.values()));
        assertThat(expectedGatewayBlocks).isPositive();
        assertThat(retries).isPositive();
        assertThat(variantsUsed).hasSizeGreaterThanOrEqualTo(17);

        System.out.printf("SEC01-SUITE200 conversations=%d modelCalls=%d retries=%d adapterAttempts=%d "
                        + "proxyRequests=%d providerRequests=%d gatewayBlocks=%d distinctVariants=%d propertyNames=%d "
                        + "outcomes=%s%n", CONVERSATIONS, expectedModelCalls, retries, adapter.size(), proxy.size(),
                provider.size(), expectedGatewayBlocks, variantsUsed.size(), seen.size(), outcomes);
    }

    /**
     * T-SEC01-HISTORY at the window boundary (security review H1): a turn with a price, a phone number and a VIN
     * is followed by seven turns. The redacted forms are replayed while the turn is inside the
     * {@value AgentTurn#MAX_HISTORY}-message window and gone after it; the raw values never leave.
     */
    @Test
    void redactedValuesAreReplayedOnlyRedactedAndLeaveTheWindow() {
        DemoHttpClient client = loggedIn("cust-a");
        String conversation = createConversation(client);
        script();
        String first = "Hôm qua báo 1.250.000đ, gọi 0912 345 678, xe VIN 5ZZAPAS11RA000001";
        assertThat(send(client, conversation, first).body()).contains("\"handedOff\":false");
        for (int turn = 2; turn <= AgentTurn.MAX_HISTORY + 2; turn++) {
            assertThat(send(client, conversation, "Lượt " + turn).body()).contains("\"handedOff\":false");
        }
        List<String> bodies = providerBodies();
        assertThat(bodies).hasSize(AgentTurn.MAX_HISTORY + 2);
        for (int i = 0; i < bodies.size(); i++) {
            String body = bodies.get(i);
            assertNoSeedValue(body, "turn " + (i + 1));
            assertThat(NEVER_EGRESS).noneMatch(body::contains);
            assertThat(body).doesNotContain("0912 345 678").doesNotContain("5ZZAPAS11RA000001").doesNotContain("1.250.000");
            boolean inWindow = i < AgentTurn.MAX_HISTORY;
            assertThat(body.contains("[SO_TIEN]") && body.contains("[SDT]") && body.contains("5ZZ**************"))
                    .as("turn %d carries the redacted first message", i + 1).isEqualTo(inWindow);
        }
        assertThat(proxyBodies()).hasSameSizeAs(bodies);
        assertThat(adapterCapture.attempts()).hasSameSizeAs(bodies);
    }

    private void runConversation(int index, Random random) {
        String customer = index % SCRIPTS == 9 ? "cust-b" : "cust-a";
        DemoHttpClient client = loggedIn(customer);
        String conversation = createConversation(client);
        int before = providerBodies().size();
        catalog.use(CatalogMode.NORMAL);
        // Every script sets its own provider responses (QA review M4, security review L8).
        script();

        switch (index % SCRIPTS) {
            case 0 -> {
                script(toolCall("searchVerifiedProduct", "{\"oe\":\"" + OE + "\"}"), text("Đã tìm thấy má phanh phù hợp."));
                answered(send(client, conversation, "Cần má phanh " + OE));
                expectCalls(2, before, Outcome.ANSWERED);
            }
            case 1 -> {
                script(text("Tôi không thể cung cấp thông tin đó, bạn liên hệ nhân viên nhé."));
                answered(send(client, conversation, "Cho tôi biết giá vốn và công nợ của tôi"));
                expectCalls(1, before, Outcome.ANSWERED);
            }
            case 2 -> {
                // Cycled through the pool with a stride coprime to its size, so the 17 runs hit 17 variants.
                String variant = variantPool.get((inboundCount++ * 11) % variantPool.size());
                variantsUsed.add(variant);
                mustNotLeave.add(variant);
                HttpResponse<String> response = send(client, conversation, "gia la " + variant);
                handedOff(response, conversation, "DLP_INBOUND");
                expectCalls(0, before, Outcome.INBOUND_BLOCKED);
            }
            case 3 -> {
                int pick = random.nextInt(SCRIPT_3_TEXTS.size());
                // "Tổng 2tr" as a phrase: a bare "2tr" can occur by chance inside a random opaque ref.
                mustNotLeave.addAll(List.of("1.250.000", "0912 345 678", "5ZZAPAS11RA000001", "Tổng 2tr"));
                answered(send(client, conversation, SCRIPT_3_TEXTS.get(pick)));
                // History tail (security review H1, QA review M1): the redacted turn is replayed twice more.
                answered(send(client, conversation, "Còn hàng không?"));
                answered(send(client, conversation, "Cảm ơn"));
                List<String> bodies = providerBodies();
                assertThat(bodies.getLast()).as("replayed redacted text").contains(SCRIPT_3_MARKERS.get(pick));
                expectCalls(3, before, Outcome.SANITIZED_HISTORY);
            }
            case 4, 5 -> {
                catalog.use(index % SCRIPTS == 4 ? CatalogMode.ERROR_WITH_SENTINEL : CatalogMode.TIMEOUT);
                // A-40(b): an ERP outage hands off at once; the error (with sentinels in the Mock ERP message)
                // never reaches a second model request, and the sinks below are still scanned for it.
                script(toolCall("searchVerifiedProduct", "{\"oe\":\"" + OE + "\"}"),
                        text("Hệ thống tra cứu đang bận, bạn thử lại sau nhé."));
                handedOff(send(client, conversation, "Cần má phanh " + OE), conversation, "CATALOG_UNAVAILABLE");
                assertThat(providerBodies()).noneMatch(body -> body.contains("CATALOG_UNAVAILABLE"));
                expectCalls(1, before, Outcome.ERP_FAILURE);
            }
            case 6 -> {
                String sentinel = DIGIT_SENTINELS.get(random.nextInt(DIGIT_SENTINELS.size()));
                Map<String, String> variants = SentinelVariants.of(sentinel);
                // Category takes letters only, so a spelled sentinel; OE takes [A-Z0-9.-], so a dotted one.
                boolean poisonOe = random.nextBoolean();
                String poison = poisonOe ? variants.get("V02-dots")
                        : variants.get(random.nextBoolean() ? "V22-spelled-vi" : "V23-spelled-en");
                mustNotLeave.add(poison);
                if (poisonOe) {
                    catalog.poisonOe(poison);
                } else {
                    catalog.poisonWith(poison);
                }
                script(toolCall("searchVerifiedProduct", "{\"oe\":\"" + OE + "\"}"), text("Chưa tìm thấy mã phù hợp."));
                HttpResponse<String> response = send(client, conversation, "Cần má phanh " + OE);
                if (poisonOe) {
                    // Layer 1: the tool manager drops a catalog row whose OE has a digit run (T-TOOL-06), so the
                    // model gets an empty result and the gateway never sees the value.
                    answered(response);
                    assertThat(providerBodies().getLast()).contains("\"candidates\":[]");
                    expectCalls(2, before, Outcome.POISONED_OE_DROPPED);
                } else {
                    // Layer 2: a spelled sentinel passes the letters-only format and is blocked by the gateway scan.
                    handedOff(response, conversation, "DLP_EGRESS");
                    expectedGatewayBlocks++;
                    expectCalls(1, before, Outcome.POISONED_CATEGORY_BLOCKED);
                }
            }
            case 7 -> {
                script(toolCall("searchVerifiedProduct", "{\"oe\":\"" + OE + "\"}"), quoteFirstCandidate(2),
                        text("Báo giá đã được gửi bên dưới."));
                HttpResponse<String> quoted = send(client, conversation, "Báo giá 2 bộ má phanh " + OE);
                assertThat(quoted.statusCode()).isEqualTo(200);
                // The customer sees the seed price on the server-rendered card; the model never does.
                assertThat(quoted.body()).contains("\"unitPrice\":\"555444333\"").contains("\"total\":\"1110888666\"");
                assertThat(providerBodies().size() - before).isEqualTo(3);
                for (String next : List.of("Cảm ơn", "Có gồm VAT không?", "Tôi sẽ xác nhận sau")) {
                    int turnStart = providerBodies().size();
                    answered(send(client, conversation, next));
                    assertThat(providerBodies().size() - turnStart).as("one model call per follow-up turn").isEqualTo(1);
                }
                expectCalls(6, before, Outcome.QUOTE_HISTORY);
            }
            case 8 -> {
                script(text("Lọc gió Innova 2020 cần mã OE để tra chính xác."));
                failNextProviderCallOnce();
                retries++;
                int attemptsBefore = adapterCapture.attempts().size();
                answered(send(client, conversation, "Lọc gió Innova 2020"));
                List<byte[]> attempts = adapterCapture.attempts();
                assertThat(attempts).hasSize(attemptsBefore + 2);
                // The retry resends the exact scanned bytes (rule 30 §7, security review L7).
                assertThat(attempts.getLast()).isEqualTo(attempts.get(attempts.size() - 2));
                expectCalls(1, before, Outcome.RETRIED);
            }
            case 9 -> {
                script(toolCall("searchVerifiedProduct", "{\"oe\":\"" + OE + "\"}"), quoteFirstCandidate(1));
                HttpResponse<String> response = send(client, conversation, "Báo giá 1 bộ " + OE);
                handedOff(response, conversation, "QUOTE_NOT_ISSUED");
                assertThat(response.body()).contains("\"quoteCard\":null");
                expectCalls(2, before, Outcome.NOT_QUOTED);
            }
            case 10 -> {
                script(text("Giá má phanh là 555.444.333 đ"));
                HttpResponse<String> response = send(client, conversation, "Giá bao nhiêu?");
                handedOff(response, conversation, "OUTPUT_REJECTED");
                assertThat(ChatBodies.withoutTurnId(response.body())).doesNotContain("555");
                expectCalls(1, before, Outcome.OUTPUT_REJECTED);
            }
            case 11 -> {
                script(toolCall("searchVerifiedProduct", "{\"oe\":\"" + OE + "\",\"customerId\":\"SYNTH-CUST-B\"}"),
                        text("Tôi chưa tra được, bạn gửi lại mã giúp tôi."));
                answered(send(client, conversation, "Cần má phanh " + OE));
                assertThat(providerBodies().getLast()).contains("\"code\":\"TOOL_SCHEMA\"");
                expectCalls(2, before, Outcome.TOOL_REJECTED);
            }
            default -> throw new IllegalStateException();
        }
    }

    private static void answered(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"handedOff\":false");
    }

    private void handedOff(HttpResponse<String> response, String conversation, String reason) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"handedOff\":true");
        assertThat(handoffReason(conversation)).isEqualTo(reason);
    }

    /** {@code calls} model calls (gateway PASS); a retry adds an adapter attempt, not a model call. */
    private void expectCalls(int calls, int before, Outcome outcome) {
        int attempts = providerBodies().size() - before;
        assertThat(attempts).as(outcome.name()).isEqualTo(outcome == Outcome.RETRIED ? calls + 1 : calls);
        expectedModelCalls += calls;
        outcomes.merge(outcome, 1, Integer::sum);
    }

    /**
     * Seed digit values, also with separators removed and after NFKC (security review M2). Identifiers such as
     * {@code cv_} are checked on egress bodies only: tables legitimately hold them.
     */
    private static void assertNoSeedValue(String text, String sink) {
        String collapsed = DIGIT_SEPARATORS.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC)).replaceAll("");
        for (String digits : SEED_DIGITS) {
            assertThat(collapsed).as(sink).doesNotContain(digits);
        }
    }

    private static void collectPropertyNames(byte[] body, Set<String> names) {
        collect(JSON.readTree(body), names);
    }

    private static void collect(JsonNode node, Set<String> names) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                names.add(entry.getKey());
                collect(entry.getValue(), names);
            }
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, names));
        }
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private enum Outcome {
        ANSWERED, INBOUND_BLOCKED, SANITIZED_HISTORY, ERP_FAILURE, POISONED_CATEGORY_BLOCKED, POISONED_OE_DROPPED,
        QUOTE_HISTORY,
        RETRIED, NOT_QUOTED, OUTPUT_REJECTED, TOOL_REJECTED
    }
}
