package com.secureerp.agent.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.DemoHttpClient;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T-SEC05-INSPECTOR, BLOCK path (IMPLEMENTATION_PLAN §2a INSPECTOR-BLOCK-TRACE; A-45(aa), (ac); owner
 * 2026-11-23, 1A–4A): a catalog row carrying the text honeytoken {@code HT-NAME-A} reaches the gateway in a
 * tool result, the egress scan blocks the model call and the turn is handed off (DLP_EGRESS). The trace SECREV
 * reads shows the decision, the policy code, the honeytoken ID, the path of the blocked field and the field
 * names built from the {@code gateway.api} records: never the honeytoken value, a sentinel, the message or an
 * identifier of the customer or conversation. Runs on the SEC-01b topology (poisoned catalog, capture proxy).
 */
class InspectorBlockTraceIT extends EgressFlowEnvironment {

    private static final String TRACES = "/api/demo/security/traces/";
    private static final String OE = "04465-0K290";
    private static final String HONEYTOKEN = "ZZHoney CustomerA";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** A JSON path made of property names and indexes only: {@code $}, {@code $.a}, {@code $.a[0].b}. */
    private static final Pattern FIELD_PATH = Pattern.compile("^\\$(\\.[A-Za-z]+(\\[\\d+])?)*$");

    @Test
    void aHoneytokenBlockShowsItsCodesAndPathButNeverItsValue() {
        DemoHttpClient customer = loggedIn("cust-a");
        String conversation = createConversation(customer);
        catalog.poisonWith(HONEYTOKEN);
        script(toolCall("searchVerifiedProduct", "{\"oe\":\"" + OE + "\"}"), text("Chưa tìm thấy mã phù hợp."));
        String message = "Cần má phanh " + OE;

        HttpResponse<String> turn = send(customer, conversation, message);

        assertThat(turn.statusCode()).as(turn.body()).isEqualTo(200);
        assertThat(turn.body()).contains("\"handedOff\":true");
        assertThat(handoffReason(conversation)).isEqualTo("DLP_EGRESS");
        // The blocked call never left: the provider got the first call only, without the honeytoken.
        assertThat(providerBodies()).hasSize(1).allSatisfy(body ->
                assertThat(normalized(body)).doesNotContain(normalized(HONEYTOKEN)));
        String requestId = turn.headers().firstValue("X-Request-Id").orElseThrow();

        HttpResponse<String> trace = loggedIn("secrev-1").get(TRACES + requestId);

        assertThat(trace.statusCode()).as(trace.body()).isEqualTo(200);
        JsonNode root = JSON.readTree(trace.body());
        List<JsonNode> egress = new ArrayList<>();
        root.get("egress").forEach(egress::add);
        assertThat(egress).extracting(row -> row.get("decision").asText()).containsExactly("PASS", "BLOCK");
        JsonNode pass = egress.get(0);
        assertThat(pass.get("policyCode").isNull()).isTrue();
        assertThat(pass.get("honeytokenId").isNull()).isTrue();
        assertThat(pass.get("blockedFieldPath").isNull()).isTrue();
        JsonNode block = egress.get(1);
        assertThat(block.get("policyCode").asText()).isEqualTo("HONEYTOKEN");
        assertThat(block.get("honeytokenId").asText()).isEqualTo("HT-NAME-A");
        String path = block.get("blockedFieldPath").asText();
        assertThat(path).matches(FIELD_PATH).isEqualTo("$.toolResults[0].candidates[0].category");
        // A-45(aa): the path and the field names are built from the gateway.api record components only.
        assertThat(propertyNames(path)).isSubsetOf(EgressSuiteFlow200IT.EXPECTED_PROPERTY_NAMES);
        for (JsonNode row : egress) {
            List<String> names = new ArrayList<>();
            row.get("fieldNames").forEach(name -> names.add(name.asText()));
            assertThat(names).isNotEmpty().allSatisfy(name ->
                    assertThat(EgressSuiteFlow200IT.EXPECTED_PROPERTY_NAMES).contains(name));
        }
        assertThat(trace.body()).contains("\"toolName\":\"searchVerifiedProduct\"");

        assertNothingRestricted(trace.body(), List.of(HONEYTOKEN, "ZZHoney", "CustomerA", conversation, message,
                "má phanh", "cust-a", "SYNTH", "usr_", "cref_", "BG-"));
        assertNothingRestrictedLeftTheApplication();
    }

    /** Property names of a field path such as {@code $.toolResults[0].candidates[0].category}. */
    private static List<String> propertyNames(String path) {
        return List.of(path.replaceAll("\\[\\d+]", "").replaceFirst("^\\$\\.?", "").split("\\."));
    }

    /** Case, spaces and separators removed: the gateway matches a text honeytoken the same way. */
    private static String normalized(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[\\s._-]+", "");
    }

    private static void assertNothingRestricted(String body, List<String> forbidden) {
        String flat = normalized(body);
        assertThat(flat).as("inspector answer").doesNotContain(normalized(HONEYTOKEN));
        for (String value : NEVER_EGRESS) {
            assertThat(body).as("inspector answer").doesNotContain(value);
        }
        for (String value : forbidden) {
            assertThat(body).as("inspector answer").doesNotContainIgnoringCase(value);
        }
    }
}
