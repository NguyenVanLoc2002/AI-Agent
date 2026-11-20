package com.secureerp.agent.conversation;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.secureerp.agent.DemoHttpClient;
import com.secureerp.agent.DemoWebEnvironment;
import com.secureerp.agent.platform.KillSwitch;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * EP-04 end to end over HTTP: web demo (profile demo, real security chain) → conversation → agent →
 * gateway → Fake provider (WireMock on loopback, A-05) → tools → Mock ERP, with PostgreSQL. The provider is
 * scripted per test; every body it received is kept for egress assertions (T-SEC01-HISTORY, SEC-02).
 * Tables are emptied before each test so per-hour limits and quote locks start fresh.
 */
public abstract class ChatEnvironment extends DemoWebEnvironment {

    public static final String COMPLETE_PATH = "/v1/fake-provider/complete";
    public static final String DEFAULT_ANSWER = "Xin chào, tôi có thể giúp gì cho bạn?";
    /** Mock ERP sentinels and identifiers that must never reach the provider (SEC-01, SEC-02). */
    public static final List<String> NEVER_EGRESS = List.of("555444333", "666777888", "987654321", "123456789",
            "SYNTH-CUST", "cref_", "usr_", "cv_", "ZZHoney", "creditBalance", "purchaseCost", "quantityOnHand",
            "5ZZAPAS11RA000001", "RLZAPAS20RA000002");

    protected static final WireMockServer PROVIDER =
            new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());

    private static final Pattern CONVERSATION_ID = Pattern.compile("\"conversationId\":\"(cv_[A-Za-z0-9_-]{22})\"");

    static {
        PROVIDER.start();
        Runtime.getRuntime().addShutdownHook(new Thread(PROVIDER::stop));
    }

    @DynamicPropertySource
    static void provider(DynamicPropertyRegistry registry) {
        registry.add("apas.gateway.provider.base-url", () -> "http://127.0.0.1:" + PROVIDER.port());
    }

    @BeforeEach
    void cleanState() {
        PROVIDER.resetAll();
        script();
        for (String table : List.of("message", "handoff", "conversation", "tool_invocation", "product_ref",
                "draft_order_event", "draft_order_request", "quote_confirmation", "quote_handle", "quote_line",
                "quote_request", "quote_lock_lift", "quote", "conversation_vin", "egress_record", "audit_event")) {
            jdbc.update("delete from " + table);
        }
        // EP-09a: the kill switch lives in the database and in a cache of the shared context; a test that
        // switched it off or tripped the breaker must not leak into the next one.
        killSwitch.set(true, TEST_OPERATOR);
        jdbc.update("delete from feature_switch_event");
    }

    /** A pseudonym used only to reset the kill switch between tests. */
    protected static final String TEST_OPERATOR = "usr_TestResetOperatorAAAAA";

    @Autowired
    protected KillSwitch killSwitch;

    /**
     * The Fake provider answers in this order (one response per model call), then the default text.
     * Responses use WireMock response templates, so a step can copy a productRef from the request's tool
     * results (the model only ever has opaque refs). The request journal is kept (it is cleared only before
     * each test), so {@link #providerBodies()} covers every turn of the test.
     */
    protected static void script(String... responses) {
        // Stubs and scenarios only: the request journal stays, so egress checks see every turn of a test.
        PROVIDER.resetMappings();
        PROVIDER.resetScenarios();
        PROVIDER.stubFor(post(urlEqualTo(COMPLETE_PATH)).atPriority(10)
                .willReturn(okJson("{\"text\":\"" + DEFAULT_ANSWER + "\"}")));
        String state = Scenario.STARTED;
        for (int i = 0; i < responses.length; i++) {
            String next = "step-" + (i + 1);
            PROVIDER.stubFor(post(urlEqualTo(COMPLETE_PATH)).atPriority(1).inScenario("turns")
                    .whenScenarioStateIs(state)
                    .willReturn(okJson(responses[i]).withTransformers("response-template"))
                    .willSetStateTo(next));
            state = next;
        }
    }

    /**
     * Like {@link #script}, then the provider answers {@code status} to the next call (EP-09a: a key revoked
     * in the middle of a turn), and {@code firstDelayMs} delays the first answer (a turn in flight).
     */
    protected static void scriptThenStatus(int status, int firstDelayMs, String... responses) {
        script(responses);
        String state = responses.length == 0 ? Scenario.STARTED : "step-" + responses.length;
        PROVIDER.stubFor(post(urlEqualTo(COMPLETE_PATH)).atPriority(1).inScenario("turns")
                .whenScenarioStateIs(state).willReturn(aResponse().withStatus(status)));
        if (firstDelayMs > 0 && responses.length > 0) {
            PROVIDER.stubFor(post(urlEqualTo(COMPLETE_PATH)).atPriority(1).inScenario("turns")
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willReturn(okJson(responses[0]).withTransformers("response-template")
                            .withFixedDelay(firstDelayMs))
                    .willSetStateTo("step-1"));
        }
    }

    /** The provider answers this status to every call (SEC-07 style failures). */
    protected static void providerStatus(int status) {
        PROVIDER.resetMappings();
        PROVIDER.stubFor(post(urlEqualTo(COMPLETE_PATH)).willReturn(aResponse().withStatus(status)));
    }

    protected static String text(String text) {
        return "{\"text\":\"" + text + "\"}";
    }

    protected static String toolCall(String name, String argumentsJson) {
        return "{\"toolProposals\":[{\"name\":\"" + name + "\",\"argumentsJson\":\""
                + argumentsJson.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}]}";
    }

    /** {@code requestQuote} for the first candidate of the first tool result of this request. */
    protected static String quoteFirstCandidate(int quantity) {
        return "{\"toolProposals\":[{\"name\":\"requestQuote\",\"argumentsJson\":\"{\\\"lines\\\":[{\\\"productRef\\\":"
                + "\\\"{{jsonPath request.body '$.toolResults[0].candidates[0].productRef'}}\\\",\\\"quantity\\\":"
                + quantity + "}]}\"}]}";
    }

    /** Bodies the Fake provider received, oldest first. */
    protected static List<String> providerBodies() {
        return PROVIDER.getAllServeEvents().stream()
                .sorted(Comparator.comparing(e -> e.getRequest().getLoggedDate()))
                .map(ServeEvent::getRequest)
                .map(request -> new String(request.getBody(), StandardCharsets.UTF_8))
                .toList();
    }

    protected static void assertNothingRestrictedLeftTheApplication() {
        for (String body : providerBodies()) {
            for (String value : NEVER_EGRESS) {
                assertThat(body).as("provider body").doesNotContain(value);
            }
        }
    }

    protected static String createConversation(DemoHttpClient client) {
        HttpResponse<String> response = client.postJson("/api/demo/conversations", "");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        Matcher matcher = CONVERSATION_ID.matcher(response.body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    protected static HttpResponse<String> send(DemoHttpClient client, String conversation, String text) {
        return send(client, conversation, UUID.randomUUID(), text);
    }

    protected static HttpResponse<String> send(DemoHttpClient client, String conversation, UUID event, String text) {
        return client.postJson("/api/demo/conversations/" + conversation + "/messages",
                "{\"clientEventId\":\"" + event + "\",\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
                        + "\"}");
    }

    protected int handoffs(String conversation) {
        return jdbc.queryForObject("select count(*) from handoff where conversation_id = ?", Integer.class,
                conversation);
    }

    protected String handoffReason(String conversation) {
        return jdbc.queryForObject("select reason_code from handoff where conversation_id = ?", String.class,
                conversation);
    }
}
