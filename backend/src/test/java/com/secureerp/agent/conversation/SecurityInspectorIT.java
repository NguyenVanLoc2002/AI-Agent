package com.secureerp.agent.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.DemoHttpClient;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * EP-09b over HTTP (UC-07, A-45): the central audit_event written by staff actions, denials and quote
 * confirmations, and the security inspector for SECREV. T-UC07-RBAC (AC-07.2), T-SEC05-INSPECTOR (AC-07.1:
 * no sentinel, price, message text or customer/conversation/quote identifier in an inspector answer).
 */
@ExtendWith(OutputCaptureExtension.class)
class SecurityInspectorIT extends ChatEnvironment {

    private static final String TRACES = "/api/demo/security/traces/";
    private static final String EVENTS = "/api/demo/security/events";
    private static final Pattern TURN_ID = Pattern.compile("\"turnId\":\"([0-9a-f-]{36})\"");
    private static final Pattern HANDOFF_ID = Pattern.compile("\"handoffId\":(\\d+)");
    private static final String QUOTE_OE = "04465-0K290";
    private static final String STAFF_TEXT = "STAFFNOTE88 xin chờ một chút";

    @Autowired
    TransactionTemplate transactions;

    private static String turnId(String body) {
        Matcher matcher = TURN_ID.matcher(body);
        assertThat(matcher.find()).as(body).isTrue();
        return matcher.group(1);
    }

    private static String handoffId(String body) {
        Matcher matcher = HANDOFF_ID.matcher(body);
        assertThat(matcher.find()).as(body).isTrue();
        return matcher.group(1);
    }

    /** A conversation of cust-a (team-1) that was handed to staff (the provider fails once). */
    private String handedOff(DemoHttpClient customer) {
        String conversation = createConversation(customer);
        providerStatus(500);
        assertThat(send(customer, conversation, "Tôi cần tư vấn má phanh").body()).contains("\"handedOff\":true");
        script();
        return conversation;
    }

    private int events(String action) {
        return jdbc.queryForObject("select count(*) from audit_event where action = ?", Integer.class, action);
    }

    /** Nothing an inspector answer may carry (TH-15, decision 6A). */
    private void assertRedacted(String body, List<String> alsoForbidden) {
        for (String value : NEVER_EGRESS) {
            if (!value.equals("usr_")) { // staff pseudonyms are shown (6A); customer ones are checked below
                assertThat(body).as("inspector answer").doesNotContain(value);
            }
        }
        assertThat(body).doesNotContain("BG-").doesNotContain("cust-a").doesNotContain("SYNTH");
        for (String value : alsoForbidden) {
            assertThat(body).as("inspector answer").doesNotContain(value);
        }
    }

    /** T-UC07-RBAC (AC-07.2): only SECREV reads traces and events; everyone else 403, anonymous 401. */
    @Test
    void onlyASecurityReviewerReadsTheInspector() {
        String trace = TRACES + UUID.randomUUID();
        for (String user : List.of("cust-a", "sales-1", "ops-1")) {
            DemoHttpClient client = loggedIn(user);
            assertThat(client.get(trace).statusCode()).as(user).isEqualTo(403);
            assertThat(client.get(EVENTS).statusCode()).as(user).isEqualTo(403);
        }
        DemoHttpClient anonymous = client();
        anonymous.csrf();
        assertThat(anonymous.get(trace).statusCode()).isEqualTo(401);
        assertThat(anonymous.get(EVENTS).statusCode()).isEqualTo(401);

        DemoHttpClient reviewer = loggedIn("secrev-1");
        assertThat(reviewer.get(trace).statusCode()).as("unknown trace").isEqualTo(404);
        assertThat(reviewer.get(EVENTS).statusCode()).isEqualTo(200);
        // Every read by the reviewer is itself audited (decision 3e).
        assertThat(jdbc.queryForList("select outcome from audit_event where action = 'INSPECTOR_READ' "
                + "and actor_role = 'SECREV' order by id", String.class)).containsExactly("NOT_FOUND", "READ");
    }

    /**
     * T-SEC05-INSPECTOR (AC-07.1): the trace of a quote turn (its request ID, A-45(h)) shows the egress
     * decisions with field names and the tool calls with their decisions, and nothing restricted: no price,
     * no message text, no conversation, quote or customer identifier. The turn ID finds the tool calls.
     */
    @Test
    void aTurnTraceShowsCodesAndFieldNamesOnly() {
        DemoHttpClient customer = loggedIn("cust-a");
        String conversation = createConversation(customer);
        script(toolCall("searchVerifiedProduct", "{\"oe\":\"" + QUOTE_OE + "\"}"), quoteFirstCandidate(2),
                text("Báo giá đã được gửi bên dưới."));
        String message = "Báo giá 2 bộ má phanh " + QUOTE_OE;
        HttpResponse<String> turn = send(customer, conversation, message);
        assertThat(turn.statusCode()).as(turn.body()).isEqualTo(200);
        String turnId = turnId(turn.body());
        String requestId = turn.headers().firstValue("X-Request-Id").orElseThrow();
        String egressTrace = jdbc.queryForObject("select trace_id from egress_record where request_id = ? "
                + "order by id limit 1", String.class, requestId);
        BigDecimal total = jdbc.queryForObject("select total from quote where conversation_id = ?", BigDecimal.class,
                conversation);

        DemoHttpClient reviewer = loggedIn("secrev-1");
        HttpResponse<String> trace = reviewer.get(TRACES + requestId);

        assertThat(trace.statusCode()).as(trace.body()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from egress_record where request_id = ?", Integer.class,
                requestId)).as("three model calls").isEqualTo(3);
        assertThat(trace.body()).contains("\"traceId\":\"" + requestId + "\"")
                .contains("\"decision\":\"PASS\"").contains("\"fieldNames\":[")
                .contains("\"toolName\":\"searchVerifiedProduct\"").contains("\"toolName\":\"requestQuote\"")
                .contains("\"decision\":\"INVOKED\"");
        assertRedacted(trace.body(), List.of(conversation, "má phanh", "Báo giá", total.stripTrailingZeros().toPlainString(),
                total.toBigInteger().toString(), "usr_"));
        assertThat(reviewer.get(TRACES + turnId).body()).contains("\"toolName\":\"requestQuote\"");
        // A-45(h), round-2 review L-3: neither the request ID nor the turn ID reaches the provider.
        assertThat(providerBodies()).isNotEmpty().allSatisfy(body ->
                assertThat(body).doesNotContain(requestId).doesNotContain(turnId));
        assertThat(PROVIDER.getAllServeEvents()).allSatisfy(event ->
                assertThat(event.getRequest().getHeaders().toString()).doesNotContain(requestId)
                        .doesNotContain(turnId));
        assertThat(reviewer.get(TRACES + egressTrace).body()).contains("\"decision\":\"PASS\"")
                .doesNotContain("requestQuote");
    }

    /**
     * A-42(j), A-43(n): claim, staff message, staff quote read and resolve are audit events with the staff
     * pseudonym and role; a claim of another team is an ACCESS_DENIED event; the reviewer sees all of them,
     * filters by action and finds the claim under the request ID the staff member got.
     */
    @Test
    void staffActionsAndDenialsAreAuditedAndVisibleToTheReviewer(CapturedOutput output) {
        DemoHttpClient customer = loggedIn("cust-a");
        String conversation = handedOff(customer);
        DemoHttpClient sales = loggedIn("sales-1");
        String handoff = handoffId(sales.get("/api/internal/handoffs").body());

        assertThat(loggedIn("sales-2").postEmpty("/api/internal/handoffs/" + handoff + "/claim").statusCode())
                .as("other team").isEqualTo(404);
        HttpResponse<String> claim = sales.postEmpty("/api/internal/handoffs/" + handoff + "/claim");
        assertThat(claim.statusCode()).isEqualTo(200);
        String claimTrace = claim.headers().firstValue("X-Request-Id").orElseThrow();
        assertThat(sales.postJson("/api/internal/conversations/" + conversation + "/messages",
                "{\"text\":\"" + STAFF_TEXT + "\"}").statusCode()).isEqualTo(204);
        assertThat(sales.get("/api/internal/conversations/" + conversation + "/quote").statusCode())
                .as("no quote in this conversation").isEqualTo(404);
        assertThat(loggedIn("sales-2").postEmpty("/api/internal/handoffs/" + handoff + "/resolve").statusCode())
                .as("other team").isEqualTo(404);
        assertThat(sales.postEmpty("/api/internal/handoffs/" + handoff + "/resolve").statusCode()).isEqualTo(200);
        assertThat(customer.get("/api/demo/quotes/BG-ZZZZZZZZ").statusCode()).isEqualTo(404);

        assertThat(events("HANDOFF_CLAIMED")).isEqualTo(1);
        assertThat(events("STAFF_MESSAGE_POSTED")).isEqualTo(1);
        assertThat(events("HANDOFF_RESOLVED")).isEqualTo(1);
        assertThat(events("STAFF_QUOTE_READ")).as("a read that found nothing is not a read").isZero();
        assertThat(jdbc.queryForList("select resource_type || ':' || outcome || ':' || actor_role from audit_event "
                + "where action = 'ACCESS_DENIED' order by id", String.class))
                .containsExactly("HANDOFF:FORBIDDEN_RESOURCE:SALES", "HANDOFF:FORBIDDEN_RESOURCE:SALES",
                        "QUOTE:FORBIDDEN_RESOURCE:CUST");
        String salesPseudonym = jdbc.queryForObject("select actor from audit_event where action = 'HANDOFF_CLAIMED'",
                String.class);
        String customerPseudonym = jdbc.queryForObject("select actor from audit_event where actor_role = 'CUST'",
                String.class);

        DemoHttpClient reviewer = loggedIn("secrev-1");
        HttpResponse<String> all = reviewer.get(EVENTS);
        assertThat(all.statusCode()).as(all.body()).isEqualTo(200);
        assertThat(all.body()).contains("\"action\":\"HANDOFF_CLAIMED\"").contains("\"action\":\"HANDOFF_RESOLVED\"")
                .contains("\"action\":\"STAFF_MESSAGE_POSTED\"").contains("\"actor\":\"" + salesPseudonym + "\"")
                .contains("\"actorRole\":\"CUST\",\"actor\":null");
        assertRedacted(all.body(), List.of(conversation, STAFF_TEXT, "STAFFNOTE88", customerPseudonym));
        HttpResponse<String> claims = reviewer.get(EVENTS + "?type=HANDOFF_CLAIMED");
        assertThat(claims.body()).contains("HANDOFF_CLAIMED").doesNotContain("HANDOFF_RESOLVED");
        HttpResponse<String> trace = reviewer.get(TRACES + claimTrace);
        assertThat(trace.statusCode()).isEqualTo(200);
        // Reviews M-1: every denial was recorded after its transaction, never with a second connection inside it.
        assertThat(output.getAll()).doesNotContain("separate_write_inside_transaction");
        assertThat(trace.body()).contains("\"action\":\"HANDOFF_CLAIMED\"").contains("\"outcome\":\"CLAIMED\"")
                .doesNotContain("HANDOFF_RESOLVED");
    }

    /** Decision 4A: kill switch changes are read from feature_switch_event and shown with the operator. */
    @Test
    void killSwitchChangesAreListedFromTheirOwnTable() {
        DemoHttpClient ops = loggedIn("ops-1");
        assertThat(ops.postJson("/api/ops/kill-switch", "{\"enabled\":false}").statusCode()).isEqualTo(200);
        assertThat(ops.postJson("/api/ops/kill-switch", "{\"enabled\":true}").statusCode()).isEqualTo(200);

        HttpResponse<String> changes = loggedIn("secrev-1").get(EVENTS + "?type=KILL_SWITCH_CHANGED");

        assertThat(changes.statusCode()).as(changes.body()).isEqualTo(200);
        assertThat(changes.body()).contains("\"outcome\":\"OFF_OPERATOR\"").contains("\"outcome\":\"ON\"")
                .contains("\"actorRole\":\"OPS\",\"actor\":\"usr_").doesNotContain("HANDOFF");
        assertThat(events("ACCESS_DENIED")).isZero();
    }

    /** Decision 5A/7A: bounded queries; anything else is a 400 and nothing is read. */
    @Test
    void malformedOrUnboundedQueriesAreRefused() {
        DemoHttpClient reviewer = loggedIn("secrev-1");
        // The server clock (test clock), not the wall clock: the window is bounded by it.
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        List<String> refused = new ArrayList<>(List.of("?limit=0", "?limit=101", "?type=DROP_TABLE",
                "?type=access_denied", "?from=yesterday", "?from=" + now + "&to=" + now,
                "?from=" + now.minus(8, ChronoUnit.DAYS) + "&to=" + now, "?type=" + "A".repeat(41), "?limit=abc",
                "?from=+300000-01-01T00:00:00Z&to=+300000-01-02T00:00:00Z",
                "?from=1970-01-01T00:00:00Z&to=1970-01-02T00:00:00Z", "?to=-1000000000-01-01T00:00:00Z",
                "?from=-1000000000-01-01T00:00:00Z&to=" + now));
        for (String query : refused) {
            assertThat(reviewer.get(EVENTS + query).statusCode()).as(query).isEqualTo(400);
        }
        assertThat(reviewer.get(TRACES + "not-a-trace").statusCode()).isEqualTo(400);
        assertThat(reviewer.get(EVENTS + "?from=" + now.minus(7, ChronoUnit.DAYS) + "&to=" + now + "&limit=100")
                .statusCode()).isEqualTo(200);
        assertThat(events("INSPECTOR_READ")).as("only the accepted read").isEqualTo(1);
    }

    /**
     * Decision 2A: a staff change and its audit record commit together. Without the audit table the staff
     * message is not stored; a denial still answers 404 although its record cannot be written.
     */
    @Test
    void aStaffChangeIsNotStoredWithoutItsAuditRecord() {
        DemoHttpClient customer = loggedIn("cust-a");
        String conversation = handedOff(customer);
        DemoHttpClient sales = loggedIn("sales-1");
        String handoff = handoffId(sales.get("/api/internal/handoffs").body());
        assertThat(sales.postEmpty("/api/internal/handoffs/" + handoff + "/claim").statusCode()).isEqualTo(200);
        int staffMessages = jdbc.queryForObject("select count(*) from message where direction = 'STAFF'",
                Integer.class);

        jdbc.execute("alter table audit_event rename to audit_event_moved");
        try {
            HttpResponse<String> post = sales.postJson("/api/internal/conversations/" + conversation + "/messages",
                    "{\"text\":\"" + STAFF_TEXT + "\"}");
            assertThat(post.statusCode()).as(post.body()).isGreaterThanOrEqualTo(500);
            assertThat(post.body()).doesNotContain("audit_event").doesNotContain(STAFF_TEXT);
            assertThat(loggedIn("sales-2").postEmpty("/api/internal/handoffs/" + handoff + "/claim").statusCode())
                    .isEqualTo(404);
        } finally {
            jdbc.execute("alter table audit_event_moved rename to audit_event");
        }
        assertThat(jdbc.queryForObject("select count(*) from message where direction = 'STAFF'", Integer.class))
                .isEqualTo(staffMessages);
        assertThat(events("STAFF_MESSAGE_POSTED")).isZero();
    }

    /** Architecture review M3: claim and resolve (with its follow-up and listeners) are rolled back as well. */
    @Test
    void aClaimOrResolveIsNotStoredWithoutItsAuditRecord() {
        DemoHttpClient customer = loggedIn("cust-a");
        String conversation = handedOff(customer);
        DemoHttpClient sales = loggedIn("sales-1");
        String handoff = handoffId(sales.get("/api/internal/handoffs").body());

        jdbc.execute("alter table audit_event rename to audit_event_moved");
        try {
            assertThat(sales.postEmpty("/api/internal/handoffs/" + handoff + "/claim").statusCode())
                    .isGreaterThanOrEqualTo(500);
        } finally {
            jdbc.execute("alter table audit_event_moved rename to audit_event");
        }
        assertThat(handoffStatus(handoff)).isEqualTo("OPEN");
        assertThat(conversationStatus(conversation)).isEqualTo("HANDOFF_PENDING");

        assertThat(sales.postEmpty("/api/internal/handoffs/" + handoff + "/claim").statusCode()).isEqualTo(200);
        jdbc.execute("alter table audit_event rename to audit_event_moved");
        try {
            assertThat(sales.postEmpty("/api/internal/handoffs/" + handoff + "/resolve").statusCode())
                    .isGreaterThanOrEqualTo(500);
        } finally {
            jdbc.execute("alter table audit_event_moved rename to audit_event");
        }
        assertThat(handoffStatus(handoff)).isEqualTo("CLAIMED");
        assertThat(conversationStatus(conversation)).isEqualTo("HUMAN_ACTIVE");
        assertThat(jdbc.queryForList("select action from audit_event order by id", String.class))
                .containsExactly("HANDOFF_CLAIMED");
    }

    /**
     * Round-2 review L-2: the handoff is resolved after the first holder check but before the post takes the
     * conversation's lock. The post answers 404, stores nothing, and the denial is recorded once, after the
     * transaction.
     */
    @Test
    void aHandoffLostBeforeTheLockIsARecordedDenial(CapturedOutput output) throws Exception {
        DemoHttpClient customer = loggedIn("cust-a");
        String conversation = handedOff(customer);
        DemoHttpClient sales = loggedIn("sales-1");
        String handoff = handoffId(sales.get("/api/internal/handoffs").body());
        assertThat(sales.postEmpty("/api/internal/handoffs/" + handoff + "/claim").statusCode()).isEqualTo(200);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> resolver = pool.submit(() -> transactions.executeWithoutResult(tx -> {
                jdbc.queryForList("select pg_advisory_xact_lock(30012, hashtext(?))", conversation);
                jdbc.update("update handoff set status = 'RESOLVED', resolved_by = claimed_by, resolved_at = now() "
                        + "where handoff_id = ?", Long.parseLong(handoff));
                locked.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<HttpResponse<String>> post = pool.submit(() -> sales.postJson(
                    "/api/internal/conversations/" + conversation + "/messages", "{\"text\":\"" + STAFF_TEXT + "\"}"));
            // The post passed the first check (the resolve is not committed) and now waits for the lock.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (jdbc.queryForObject("select count(*) from pg_locks where locktype = 'advisory' and not granted",
                    Integer.class) == 0) {
                assertThat(System.nanoTime()).as("post waiting for the lock").isLessThan(deadline);
                Thread.onSpinWait();
            }
            release.countDown();
            resolver.get(10, TimeUnit.SECONDS);
            assertThat(post.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(404);
        }
        assertThat(jdbc.queryForObject("select count(*) from message where direction = 'STAFF'", Integer.class))
                .isZero();
        assertThat(events("STAFF_MESSAGE_POSTED")).isZero();
        assertThat(jdbc.queryForList("select resource_type || ':' || outcome from audit_event "
                + "where action = 'ACCESS_DENIED'", String.class)).containsExactly("CONVERSATION:FORBIDDEN_RESOURCE");
        assertThat(output.getAll()).doesNotContain("separate_write_inside_transaction");
    }

    private String handoffStatus(String handoff) {
        return jdbc.queryForObject("select status from handoff where handoff_id = ?", String.class,
                Long.parseLong(handoff));
    }

    private String conversationStatus(String conversation) {
        return jdbc.queryForObject("select status from conversation where conversation_id = ?", String.class,
                conversation);
    }
}
