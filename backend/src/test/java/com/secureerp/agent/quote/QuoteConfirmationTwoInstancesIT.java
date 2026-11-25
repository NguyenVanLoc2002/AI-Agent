package com.secureerp.agent.quote;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.DemoHttpClient;
import com.secureerp.agent.SecureErpAgentApplication;
import com.secureerp.agent.conversation.ChatEnvironment;
import com.secureerp.agent.erp.internal.MockErpConnector;
import com.secureerp.agent.erp.internal.MockErpOrderFault;
import com.secureerp.agent.erp.internal.MockErpSimulation;
import com.secureerp.agent.platform.MutableClock;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * SEC04-PRINCIPALS (A-65, owner 2026-11-25 1A–4A): SEC-04 across two application instances on one
 * PostgreSQL. The first instance is the shared test context; the second is a separate Spring context
 * started here on its own port with its own test clock and its own Mock ERP, like a second node behind
 * a load balancer. The only state the two share is the database, so exactly one draft order across
 * both can only come from the database guards (unique request per quote version, conditional status
 * updates, advisory locks; ADR-009, A-36f, A-41).
 *
 * <p>"Ten principals" is ten separate logged-in sessions of the quote's owner, five on each instance
 * (1A): a quote belongs to one channel binding, so another user's confirmation is a 404 (SEC-02,
 * {@code QuoteConfirmationIT}) and never reaches the race. Several accounts for one ERP customer do
 * not exist in the PoC.
 */
class QuoteConfirmationTwoInstancesIT extends ChatEnvironment {

    private static final String OE = "04465-0K290";
    private static final String PRICE_A = "555444333";

    /** The second instance; started once for this class, closed after it. */
    private static ConfigurableApplicationContext second;

    @Autowired
    MockErpConnector mockErp;

    @Autowired
    PostgreSQLContainer postgres;

    private MockErpSimulation erp1;
    private MockErpSimulation erp2;

    @BeforeEach
    void startSecondInstanceAndResetBothErps() {
        if (second == null) {
            second = new SpringApplicationBuilder(SecureErpAgentApplication.class)
                    .profiles("demo")
                    .initializers(context -> ((GenericApplicationContext) context).registerBean("testClock",
                            MutableClock.class, () -> new MutableClock(START), bean -> bean.setPrimary(true)))
                    // Command-line arguments: they outrank application.yml (default properties do not).
                    .run("--server.port=0",
                            "--server.address=127.0.0.1",
                            "--spring.main.banner-mode=off",
                            "--spring.jmx.enabled=false",
                            "--spring.datasource.url=" + postgres.getJdbcUrl(),
                            "--spring.datasource.username=" + postgres.getUsername(),
                            "--spring.datasource.password=" + postgres.getPassword(),
                            "--apas.identity.demo-password=" + PASSWORD,
                            "--apas.gateway.provider.base-url=http://127.0.0.1:" + PROVIDER.port());
        }
        // Frozen at START like the first instance's clock. The kill switch is never switched off in this
        // class, so its cache on the second instance never holds a stale "off"; a test that switches it
        // must advance this clock past the cache lifetime too.
        second.getBean(MutableClock.class).set(START);
        erp1 = mockErp.simulation();
        erp1.reset();
        erp2 = second.getBean(MockErpConnector.class).simulation();
        erp2.reset();
    }

    @AfterAll
    static void closeSecondInstance() {
        try {
            if (second != null) {
                second.close();
            }
        } finally {
            second = null;
        }
    }

    private static int secondPort() {
        return ((WebServerApplicationContext) second).getWebServer().getPort();
    }

    /** A client of the second instance, logged in as a synthetic demo user. */
    private static DemoHttpClient loggedInOnSecond(String username) {
        DemoHttpClient client = new DemoHttpClient(secondPort());
        int status = client.login(username, PASSWORD).statusCode();
        if (status != 200) {
            throw new AssertionError("login of " + username + " on the second instance failed with " + status);
        }
        return client;
    }

    /** What the customer's browser holds after a quote was issued in the chat. */
    private record Issued(String conversation, String quoteRef, int version, String token) {
    }

    /** Issues a quote through the chat of the first instance. */
    private Issued quote(DemoHttpClient client) {
        String conversation = createConversation(client);
        script(toolCall("searchVerifiedProduct", "{\"oe\":\"" + OE + "\"}"), quoteFirstCandidate(2),
                text("Báo giá đã được gửi bên dưới."));
        HttpResponse<String> response = send(client, conversation, "Báo giá 2 bộ má phanh " + OE);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return new Issued(conversation, field(response.body(), "quoteRef"),
                Integer.parseInt(number(response.body(), "version")), field(response.body(), "confirmationToken"));
    }

    private static String field(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\":\"([^\"]+)\"").matcher(json);
        assertThat(matcher.find()).as("%s in %s", name, json).isTrue();
        return matcher.group(1);
    }

    private static String number(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\":(\\d+)").matcher(json);
        assertThat(matcher.find()).as("%s in %s", name, json).isTrue();
        return matcher.group(1);
    }

    private static HttpResponse<String> confirm(DemoHttpClient client, Issued quote) {
        return client.postJson("/api/demo/quotes/" + quote.quoteRef() + "/confirm",
                "{\"version\":" + quote.version() + ",\"confirmationToken\":\"" + quote.token() + "\"}");
    }

    private List<String> statuses(String quoteRef) {
        return jdbc.queryForList("select status from quote where public_ref = ? order by version", String.class,
                quoteRef);
    }

    private int requests() {
        return jdbc.queryForObject("select count(*) from draft_order_request", Integer.class);
    }

    private List<String> attempts() {
        return jdbc.queryForList("select outcome from quote_confirmation order by id", String.class);
    }

    /**
     * Positive control: the second instance is a separate node (own port, own Mock ERP) and can on its
     * own confirm a quote issued on the first one. Without this, "one ERP call in total" in the race
     * below could mean the second instance never got as far as the ERP.
     */
    @Test
    void theSecondInstanceIsASeparateNodeThatCanConfirmAQuoteIssuedOnTheFirst() {
        assertThat(secondPort()).isNotEqualTo(port);
        assertThat(erp2).isNotSameAs(erp1);
        Issued quote = quote(loggedIn("cust-a"));

        HttpResponse<String> confirmed = confirm(loggedInOnSecond("cust-a"), quote);

        assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(200);
        assertThat(confirmed.body()).contains("\"status\":\"DRAFT_ORDER_CREATED\"");
        assertThat(erp2.createCalls()).isEqualTo(1);
        assertThat(erp1.createCalls()).isZero();
        assertThat(requests()).isEqualTo(1);
        assertThat(statuses(quote.quoteRef())).containsExactly("DRAFT_ORDER_CREATED");
    }

    /** One confirmation as the client saw it: when it was sent and when the answer came. */
    private record Timed(HttpResponse<String> response, Instant sent, Instant answered) {
    }

    /**
     * T-SEC04-PARALLEL across instances (AC-04.1): ten sessions of the owner, five on each instance,
     * confirm the same quote version at once → one {@code draft_order_request}, one ERP call over both
     * Mock ERPs, one draft, one accepted attempt.
     *
     * <p>The latch releases the ten requests together, but nothing holds one confirmation inside the
     * service while the others arrive (the Mock ERP answers at once), so overlap between reading the
     * quote and committing the confirmation is likely, not guaranteed. The overlap seen by the clients
     * is printed ({@code SEC04-OVERLAP}), not asserted; a deterministic overlap needs a Mock ERP latency
     * (IMPLEMENTATION_PLAN §2a SEC04-OVERLAP, PROPOSED).
     */
    @Test
    void tenSessionsOnTwoInstancesConfirmingAtOnceCreateExactlyOneDraft() throws Exception {
        DemoHttpClient first = loggedIn("cust-a");
        Issued quote = quote(first);
        List<DemoHttpClient> clients = new ArrayList<>(List.of(first));
        for (int i = 1; i < 5; i++) {
            clients.add(loggedIn("cust-a"));
        }
        for (int i = 0; i < 5; i++) {
            clients.add(loggedInOnSecond("cust-a"));
        }
        ExecutorService pool = Executors.newFixedThreadPool(clients.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Timed>> results = new ArrayList<>();
        List<Timed> answers = new ArrayList<>();
        try {
            for (DemoHttpClient client : clients) {
                Callable<Timed> call = () -> {
                    start.await();
                    Instant sent = Instant.now();
                    HttpResponse<String> response = confirm(client, quote);
                    return new Timed(response, sent, Instant.now());
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            for (Future<Timed> result : results) {
                Timed timed = result.get(60, TimeUnit.SECONDS);
                answers.add(timed);
                HttpResponse<String> response = timed.response();
                assertThat(response.statusCode()).as(response.body()).isIn(200, 202);
                assertThat(response.body()).containsAnyOf("\"status\":\"DRAFT_ORDER_CREATED\"",
                        "\"status\":\"ORDER_SUBMITTING\"").doesNotContain(PRICE_A);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(erp1.createCalls() + erp2.createCalls()).as("ERP calls over both instances").isEqualTo(1);
        assertThat(erp1.draftCount() + erp2.draftCount()).isEqualTo(1);
        assertThat(requests()).isEqualTo(1);
        assertThat(statuses(quote.quoteRef())).containsExactly("DRAFT_ORDER_CREATED");
        Instant firstAnswer = answers.stream().map(Timed::answered).min(Instant::compareTo).orElseThrow();
        long inFlight = answers.stream().filter(timed -> timed.sent().isBefore(firstAnswer)).count();
        System.out.println("SEC04-OVERLAP sentBeforeFirstAnswer=" + inFlight + " of " + answers.size());
        assertThat(attempts()).as("every session reached the service: %s",
                answers.stream().map(timed -> timed.response().statusCode()).toList()).hasSize(10);
        assertThat(attempts()).filteredOn("ACCEPTED"::equals).hasSize(1);
        assertThat(confirm(loggedInOnSecond("cust-a"), quote).body()).contains("\"status\":\"DRAFT_ORDER_CREATED\"");
        assertThat(confirm(first, quote).body()).contains("\"status\":\"DRAFT_ORDER_CREATED\"");
        assertThat(erp1.createCalls() + erp2.createCalls()).isEqualTo(1);
    }

    /**
     * T-SEC04-UNKNOWN across instances (AC-04.3, BR-19): the first instance's ERP wrote the draft but the
     * answer was lost. The second instance, whose ERP would answer, never sends it again: the outcome
     * is unknown for both, with one request and one handoff.
     */
    @Test
    void anUnknownOutcomeOnOneInstanceIsNeverSentAgainFromTheOther() {
        DemoHttpClient onFirst = loggedIn("cust-a");
        Issued quote = quote(onFirst);
        erp1.failOrders(MockErpOrderFault.TIMEOUT_AFTER_COMMIT);

        HttpResponse<String> unknown = confirm(onFirst, quote);

        assertThat(unknown.statusCode()).as(unknown.body()).isEqualTo(202);
        assertThat(unknown.body()).contains("\"status\":\"ORDER_OUTCOME_UNKNOWN\"").contains("\"handedOff\":true");

        DemoHttpClient onSecond = loggedInOnSecond("cust-a");
        for (int i = 0; i < 2; i++) {
            HttpResponse<String> again = confirm(onSecond, quote);
            assertThat(again.statusCode()).as(again.body()).isEqualTo(202);
            assertThat(again.body()).contains("\"status\":\"ORDER_OUTCOME_UNKNOWN\"").doesNotContain(PRICE_A);
        }
        assertThat(erp2.createCalls()).as("no write from the second instance").isZero();
        assertThat(erp2.lookupCalls()).isZero();
        assertThat(erp1.createCalls()).isEqualTo(1);
        assertThat(erp1.draftCount() + erp2.draftCount()).isEqualTo(1);
        assertThat(requests()).isEqualTo(1);
        assertThat(statuses(quote.quoteRef())).containsExactly("ORDER_OUTCOME_UNKNOWN");
        assertThat(handoffReason(quote.conversation())).isEqualTo("ERP_UNKNOWN");
        assertThat(handoffs(quote.conversation())).isEqualTo(1);
    }
}
