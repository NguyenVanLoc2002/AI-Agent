package com.secureerp.agent.quote;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.DemoHttpClient;
import com.secureerp.agent.conversation.ChatEnvironment;
import com.secureerp.agent.erp.internal.MockErpConnector;
import com.secureerp.agent.erp.internal.MockErpFaultMode;
import com.secureerp.agent.erp.internal.MockErpOrderFault;
import com.secureerp.agent.erp.internal.MockErpSimulation;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * EP-07 end to end over HTTP (UC-04, DATA_FLOW §2): chat → quote card with its confirm control →
 * {@code POST /api/demo/quotes/{ref}/confirm} → order → Mock ERP, with PostgreSQL and the real
 * security chain. T-UC04-GUARDS, -OPTLOCK (stale after a newer quote), -BLOCKED, -REPRICE, -CHAT-OK;
 * T-SEC04-PARALLEL, -DUPCALLBACK (a repeated confirmation), -UNKNOWN with staff reconciliation.
 */
@ExtendWith(OutputCaptureExtension.class)
class QuoteConfirmationIT extends ChatEnvironment {

    private static final String OE = "04465-0K290";
    private static final String SKU = "SKU-BRK-PAD-A";
    private static final String ERP_CUSTOMER_A = "SYNTH-CUST-A";
    private static final String PRICE_A = "555444333";

    @Autowired
    MockErpConnector mockErp;

    private MockErpSimulation erp;

    @BeforeEach
    void resetErp() {
        erp = mockErp.simulation();
        erp.reset();
    }

    /** What the customer's browser holds after a quote was issued in the chat. */
    private record Issued(String conversation, String quoteRef, int version, String token) {
    }

    private Issued quote(DemoHttpClient client, int quantity) {
        String conversation = createConversation(client);
        return quoteIn(client, conversation, quantity);
    }

    private Issued quoteIn(DemoHttpClient client, String conversation, int quantity) {
        script(toolCall("searchVerifiedProduct", "{\"oe\":\"" + OE + "\"}"), quoteFirstCandidate(quantity),
                text("Báo giá đã được gửi bên dưới."));
        HttpResponse<String> response = send(client, conversation, "Báo giá " + quantity + " bộ má phanh " + OE);
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
        return confirm(client, quote.quoteRef(), quote.version(), quote.token());
    }

    private static HttpResponse<String> confirm(DemoHttpClient client, String quoteRef, int version, String token) {
        return client.postJson("/api/demo/quotes/" + quoteRef + "/confirm",
                "{\"version\":" + version + ",\"confirmationToken\":\"" + token + "\"}");
    }

    private List<String> statuses(String quoteRef) {
        return jdbc.queryForList("select status from quote where public_ref = ? order by version", String.class,
                quoteRef);
    }

    private int requests() {
        return jdbc.queryForObject("select count(*) from draft_order_request", Integer.class);
    }

    private String conversationStatus(String conversation) {
        return jdbc.queryForObject("select status from conversation where conversation_id = ?", String.class,
                conversation);
    }

    private List<String> attempts() {
        return jdbc.queryForList("select outcome from quote_confirmation order by id", String.class);
    }

    /** AC-04 happy path + T-SEC04-DUPCALLBACK: one draft; repeating the confirmation changes nothing. */
    @Test
    void aConfirmedQuoteCreatesOneDraftOrderAndARepeatChangesNothing(CapturedOutput output) {
        DemoHttpClient client = loggedIn("cust-a");
        Issued quote = quote(client, 2);

        HttpResponse<String> confirmed = confirm(client, quote);

        assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(200);
        assertThat(confirmed.body()).contains("\"status\":\"DRAFT_ORDER_CREATED\"").contains("\"handedOff\":false")
                .contains("\"quoteRef\":\"" + quote.quoteRef() + "\"").doesNotContain(PRICE_A);
        assertThat(statuses(quote.quoteRef())).containsExactly("DRAFT_ORDER_CREATED");
        assertThat(erp.draftCount()).isEqualTo(1);
        assertThat(erp.draftExternalRefs()).containsExactly(quote.quoteRef() + ":1");
        assertThat(jdbc.queryForObject("select status || ':' || idempotency_key from draft_order_request",
                String.class)).isEqualTo("CREATED:" + quote.quoteRef() + ":1");
        assertThat(conversationStatus(quote.conversation())).isEqualTo("ACTIVE_BOT");

        for (int i = 0; i < 3; i++) {
            HttpResponse<String> again = confirm(client, quote);
            assertThat(again.statusCode()).isEqualTo(200);
            assertThat(again.body()).contains("\"status\":\"DRAFT_ORDER_CREATED\"");
        }
        assertThat(erp.createCalls()).isEqualTo(1);
        assertThat(erp.draftCount()).isEqualTo(1);
        assertThat(requests()).isEqualTo(1);
        assertThat(attempts()).containsExactly("ACCEPTED", "REPLAY", "REPLAY", "REPLAY");

        // The confirmed quote is no longer a card to confirm, and nothing of it went to the provider or a log.
        assertThat(client.get("/api/demo/quotes/" + quote.quoteRef()).body())
                .contains("\"status\":\"DRAFT_ORDER_CREATED\"").contains("\"quoteCard\":null");
        assertNothingRestrictedLeftTheApplication();
        for (String body : providerBodies()) {
            assertThat(body).doesNotContain(quote.token()).doesNotContain(quote.quoteRef());
        }
        assertThat(output.getAll()).doesNotContain(quote.token()).doesNotContain(quote.quoteRef())
                .doesNotContain(PRICE_A).doesNotContain(ERP_CUSTOMER_A);
        assertThat(jdbc.queryForObject("select confirm_token_hash from quote", String.class))
                .hasSize(64).doesNotContain(quote.token());
    }

    /** T-SEC04-PARALLEL (AC-04.1): ten confirmations at once, exactly one draft order. */
    @Test
    void tenParallelConfirmationsCreateExactlyOneDraft() throws Exception {
        DemoHttpClient first = loggedIn("cust-a");
        Issued quote = quote(first, 2);
        List<DemoHttpClient> clients = new ArrayList<>(List.of(first));
        for (int i = 1; i < 10; i++) {
            clients.add(loggedIn("cust-a"));
        }
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> results = new ArrayList<>();
        try {
            for (DemoHttpClient client : clients) {
                Callable<HttpResponse<String>> call = () -> {
                    start.await();
                    return confirm(client, quote);
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            for (Future<HttpResponse<String>> result : results) {
                HttpResponse<String> response = result.get();
                assertThat(response.statusCode()).as(response.body()).isIn(200, 202);
                assertThat(response.body()).containsAnyOf("\"status\":\"DRAFT_ORDER_CREATED\"",
                        "\"status\":\"ORDER_SUBMITTING\"");
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(erp.createCalls()).isEqualTo(1);
        assertThat(erp.draftCount()).isEqualTo(1);
        assertThat(requests()).isEqualTo(1);
        assertThat(statuses(quote.quoteRef())).containsExactly("DRAFT_ORDER_CREATED");
        assertThat(attempts()).filteredOn("ACCEPTED"::equals).hasSize(1);
        assertThat(confirm(first, quote).body()).contains("\"status\":\"DRAFT_ORDER_CREATED\"");
    }

    /** T-UC04-GUARDS + SEC-02: who may confirm, and with what. Nothing is created by any of these. */
    @Test
    void onlyTheOwnerWithTheCardsTokenAndVersionCanConfirm() {
        DemoHttpClient owner = loggedIn("cust-a");
        Issued quote = quote(owner, 2);
        String path = "/api/demo/quotes/" + quote.quoteRef() + "/confirm";
        String body = "{\"version\":1,\"confirmationToken\":\"" + quote.token() + "\"}";

        DemoHttpClient anonymous = client();
        anonymous.csrf();
        assertThat(anonymous.postJson(path, body).statusCode()).isEqualTo(401);
        DemoHttpClient noCsrf = loggedIn("cust-a");
        noCsrf.dropCsrf();
        assertThat(noCsrf.postJson(path, body).statusCode()).isEqualTo(403);
        for (String staff : List.of("sales-1", "ops-1", "secrev-1")) {
            HttpResponse<String> refused = confirm(loggedIn(staff), quote);
            assertThat(refused.statusCode()).as(staff).isEqualTo(403);
            assertThat(refused.body()).contains("\"code\":\"FORBIDDEN\"");
        }
        assertThat(confirm(loggedIn("cust-new"), quote).body()).contains("\"code\":\"CUSTOMER_NOT_LINKED\"");

        // Another customer, even with the real reference, version and token: the same 404 as a missing quote.
        DemoHttpClient other = loggedIn("cust-b");
        for (HttpResponse<String> response : List.of(confirm(other, quote),
                other.get("/api/demo/quotes/" + quote.quoteRef()),
                confirm(owner, quote.quoteRef(), 1, "qc_AAAAAAAAAAAAAAAAAAAAAA"),
                confirm(owner, quote.quoteRef(), 2, quote.token()),
                confirm(owner, "BG-ZZZZZZZZ", 1, quote.token()),
                confirm(owner, "1%20or%201=1", 1, quote.token()),
                confirm(owner, quote.quoteRef(), 1, "ok"))) {
            assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
            // The problem body repeats only the path the caller sent (its "instance"), nothing of the quote.
            assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"").doesNotContain(PRICE_A)
                    .doesNotContain("ISSUED").doesNotContain("version").doesNotContain("quoteCard");
        }
        // Syntactically invalid bodies, including a smuggled customer field, never reach the service.
        for (String invalid : List.of("{\"version\":0,\"confirmationToken\":\"" + quote.token() + "\"}",
                "{\"confirmationToken\":\"" + quote.token() + "\"}", "{\"version\":1}",
                "{\"version\":1,\"confirmationToken\":\"" + quote.token() + "\",\"customerId\":\"SYNTH-CUST-B\"}",
                "{\"version\":1,\"confirmationToken\":\"" + quote.token() + "\",\"unitPrice\":1}")) {
            assertThat(owner.postJson(path, invalid).statusCode()).as(invalid).isEqualTo(400);
        }

        assertThat(statuses(quote.quoteRef())).containsExactly("ISSUED");
        assertThat(requests()).isZero();
        assertThat(erp.createCalls()).isZero();
        // The owner still can.
        assertThat(confirm(owner, quote).body()).contains("\"status\":\"DRAFT_ORDER_CREATED\"");
        assertThat(erp.draftCount()).isEqualTo(1);
    }

    /** AC-04.2: an expired quote is 410 and stays so. */
    @Test
    void anExpiredQuoteIsGone() {
        Issued quote = quote(loggedIn("cust-a"), 2);
        clock.advance(Duration.ofHours(24));
        DemoHttpClient later = loggedIn("cust-a");

        for (int i = 0; i < 2; i++) {
            HttpResponse<String> response = confirm(later, quote);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(410);
            assertThat(response.body()).contains("\"code\":\"QUOTE_EXPIRED\"");
        }
        assertThat(statuses(quote.quoteRef())).containsExactly("EXPIRED");
        assertThat(later.get("/api/demo/quotes/" + quote.quoteRef()).body()).contains("\"status\":\"EXPIRED\"")
                .contains("\"quoteCard\":null");
        assertThat(requests()).isZero();
        assertThat(erp.createCalls()).isZero();
    }

    /** AC-04.2 / T-UC04-OPTLOCK: a quote replaced by a newer one in the conversation is stale (409). */
    @Test
    void aQuoteSupersededByANewerOneIsStale() {
        DemoHttpClient client = loggedIn("cust-a");
        Issued old = quote(client, 2);
        Issued current = quoteIn(client, old.conversation(), 3);
        assertThat(current.quoteRef()).isNotEqualTo(old.quoteRef());

        HttpResponse<String> stale = confirm(client, old);

        assertThat(stale.statusCode()).as(stale.body()).isEqualTo(409);
        assertThat(stale.body()).contains("\"code\":\"QUOTE_STALE_VERSION\"");
        assertThat(requests()).isZero();
        assertThat(confirm(client, current).body()).contains("\"status\":\"DRAFT_ORDER_CREATED\"");
        assertThat(erp.draftExternalRefs()).containsExactly(current.quoteRef() + ":1");
        assertThat(statuses(old.quoteRef())).containsExactly("SUPERSEDED");
    }

    /** T-UC04-REPRICE (AC-04.4, BR-16): a changed price issues a new version that needs its own confirmation. */
    @Test
    void aChangedPriceIssuesANewVersionThatNeedsAFreshConfirmation() {
        DemoHttpClient client = loggedIn("cust-a");
        Issued v1 = quote(client, 2);
        erp.listPrice(ERP_CUSTOMER_A, SKU, "555444000");

        HttpResponse<String> repriced = confirm(client, v1);

        assertThat(repriced.statusCode()).as(repriced.body()).isEqualTo(409);
        assertThat(repriced.body()).contains("\"code\":\"QUOTE_REPRICED\"").doesNotContain("555444");
        assertThat(statuses(v1.quoteRef())).containsExactly("SUPERSEDED", "ISSUED");
        assertThat(requests()).isZero();
        assertThat(erp.createCalls()).isZero();
        // The issued snapshot of version 1 is untouched (rule 20: never mutate an issued price).
        assertThat(jdbc.queryForList("select l.unit_price::bigint::text from quote_line l join quote q "
                + "on q.id = l.quote_id where q.public_ref = ? order by q.version", String.class, v1.quoteRef()))
                .containsExactly(PRICE_A, "555444000");

        String v2Body = client.get("/api/demo/quotes/" + v1.quoteRef()).body();
        assertThat(v2Body).contains("\"version\":2").contains("\"status\":\"ISSUED\"")
                .contains("\"unitPrice\":\"555444000\"").contains("\"total\":\"1110888000\"");
        String v2Token = field(v2Body, "confirmationToken");
        assertThat(v2Token).isNotEqualTo(v1.token());

        // The old version, and the old token on the new version, confirm nothing.
        assertThat(confirm(client, v1).body()).contains("\"code\":\"QUOTE_STALE_VERSION\"");
        assertThat(confirm(client, v1.quoteRef(), 2, v1.token()).statusCode()).isEqualTo(404);
        assertThat(requests()).isZero();

        HttpResponse<String> confirmed = confirm(client, v1.quoteRef(), 2, v2Token);
        assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(200);
        assertThat(confirmed.body()).contains("\"status\":\"DRAFT_ORDER_CREATED\"").contains("\"version\":2");
        assertThat(erp.draftExternalRefs()).containsExactly(v1.quoteRef() + ":2");
        assertThat(statuses(v1.quoteRef())).containsExactly("SUPERSEDED", "DRAFT_ORDER_CREATED");
        assertNothingRestrictedLeftTheApplication();
    }

    /**
     * T-UC04-BLOCKED (AC-04.6, BR-38): stock, credit or price no longer allow the order → blocked,
     * handed to staff, no order, and the customer is not told why (A-14).
     */
    @ParameterizedTest
    @CsvSource({"STOCK,INSUFFICIENT_STOCK", "CREDIT,CREDIT_NOT_ELIGIBLE", "PRICE,PRICE_NOT_FOUND"})
    void aFailedRecheckBlocksTheConfirmationAndHandsOff(String change, String internalReason, CapturedOutput output) {
        DemoHttpClient client = loggedIn("cust-a");
        Issued quote = quote(client, 2);
        switch (change) {
            case "STOCK" -> erp.stock(SKU, 1);
            case "CREDIT" -> erp.creditStatus(ERP_CUSTOMER_A, "BLOCKED");
            default -> erp.delistPrice(ERP_CUSTOMER_A, SKU);
        }

        HttpResponse<String> blocked = confirm(client, quote);

        assertThat(blocked.statusCode()).as(blocked.body()).isEqualTo(200);
        assertThat(blocked.body()).contains("\"status\":\"CONFIRMATION_BLOCKED\"").contains("\"handedOff\":true")
                .doesNotContain(internalReason).doesNotContain("STOCK").doesNotContain("CREDIT")
                .doesNotContain(PRICE_A);
        assertThat(statuses(quote.quoteRef())).containsExactly("CONFIRMATION_BLOCKED");
        assertThat(handoffReason(quote.conversation())).isEqualTo("QUOTE_NOT_ISSUED");
        assertThat(conversationStatus(quote.conversation())).isEqualTo("HANDOFF_PENDING");
        assertThat(jdbc.queryForObject("select reason_code from quote_confirmation where outcome = 'BLOCKED'",
                String.class)).isEqualTo(internalReason);
        assertThat(output.getAll()).doesNotContain(internalReason);

        // The same answer again, even once the ERP would allow it; the bot stays locked without a model call.
        erp.reset();
        assertThat(confirm(client, quote).body()).contains("\"status\":\"CONFIRMATION_BLOCKED\"");
        int providerCalls = providerBodies().size();
        assertThat(send(client, quote.conversation(), "vì sao không đặt được?").body()).contains("\"handedOff\":true");
        assertThat(providerBodies()).hasSize(providerCalls);
        assertThat(requests()).isZero();
        assertThat(erp.createCalls()).isZero();
        assertThat(handoffs(quote.conversation())).isEqualTo(1);
    }

    /**
     * T-SEC04-UNKNOWN (AC-04.3, BR-19): the ERP wrote the draft but the answer was lost. The outcome is
     * unknown, staff are involved, nothing is sent again, and SALES record what they found in the ERP.
     */
    @Test
    void aLostErpAnswerIsUnknownIsNeverSentAgainAndIsReconciledByStaff(CapturedOutput output) {
        DemoHttpClient client = loggedIn("cust-a");
        Issued quote = quote(client, 2);
        erp.failOrders(MockErpOrderFault.TIMEOUT_AFTER_COMMIT);

        HttpResponse<String> unknown = confirm(client, quote);

        assertThat(unknown.statusCode()).as(unknown.body()).isEqualTo(202);
        assertThat(unknown.body()).contains("\"status\":\"ORDER_OUTCOME_UNKNOWN\"").contains("\"handedOff\":true");
        assertThat(statuses(quote.quoteRef())).containsExactly("ORDER_OUTCOME_UNKNOWN");
        assertThat(handoffReason(quote.conversation())).isEqualTo("ERP_UNKNOWN");
        assertThat(conversationStatus(quote.conversation())).isEqualTo("HANDOFF_PENDING");

        erp.failOrders(MockErpOrderFault.NONE);
        for (int i = 0; i < 2; i++) {
            assertThat(confirm(client, quote).body()).contains("\"status\":\"ORDER_OUTCOME_UNKNOWN\"");
        }
        assertThat(erp.createCalls()).as("no second write").isEqualTo(1);
        assertThat(erp.draftCount()).isEqualTo(1);
        assertThat(erp.lookupCalls()).isZero();
        assertThat(requests()).isEqualTo(1);

        // The queue is for SALES only.
        for (String notSales : List.of("cust-a", "ops-1", "secrev-1")) {
            assertThat(loggedIn(notSales).get("/api/internal/orders/unknown").statusCode()).as(notSales).isEqualTo(403);
        }
        assertThat(client().get("/api/internal/orders/unknown").statusCode()).isEqualTo(401);
        DemoHttpClient sales = loggedIn("sales-1");
        HttpResponse<String> queue = sales.get("/api/internal/orders/unknown");
        assertThat(queue.statusCode()).as(queue.body()).isEqualTo(200);
        assertThat(queue.body()).contains("\"externalRef\":\"" + quote.quoteRef() + ":1\"").doesNotContain(PRICE_A)
                .doesNotContain(ERP_CUSTOMER_A).doesNotContain("cref_");
        String reconcile = "/api/internal/orders/" + number(queue.body(), "requestId") + "/reconcile";
        assertThat(client.postJson(reconcile, "{\"outcome\":\"ABSENT\"}").statusCode()).isEqualTo(403);
        // EP-08a: the queue belongs to the team serving the customer; another team sees and changes nothing.
        DemoHttpClient otherTeam = loggedIn("sales-2");
        assertThat(otherTeam.get("/api/internal/orders/unknown").body()).isEqualTo("[]");
        assertThat(otherTeam.postJson(reconcile, "{\"outcome\":\"ABSENT\"}").statusCode()).isEqualTo(404);
        assertThat(sales.postJson(reconcile, "{\"outcome\":\"CREATED\"}").statusCode()).isEqualTo(400);

        HttpResponse<String> reconciled =
                sales.postJson(reconcile, "{\"outcome\":\"CREATED\",\"erpOrderRef\":\"MOCK-DO-000001\"}");

        assertThat(reconciled.statusCode()).as(reconciled.body()).isEqualTo(200);
        assertThat(reconciled.body()).contains("\"status\":\"RECONCILED_CREATED\"");
        assertThat(statuses(quote.quoteRef())).containsExactly("DRAFT_ORDER_CREATED");
        assertThat(sales.get("/api/internal/orders/unknown").body()).isEqualTo("[]");
        assertThat(sales.postJson(reconcile, "{\"outcome\":\"ABSENT\"}").statusCode()).isEqualTo(409);
        HttpResponse<String> after = confirm(client, quote);
        assertThat(after.statusCode()).isEqualTo(200);
        assertThat(after.body()).contains("\"status\":\"DRAFT_ORDER_CREATED\"");
        assertThat(erp.createCalls()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from draft_order_event where event = 'RECONCILED_CREATED' "
                + "and actor like 'usr\\_%'", Integer.class)).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain("123456789").doesNotContain(PRICE_A);
    }

    /** Reconciliation finds no draft → the quote failed; the customer gets no order and no second attempt. */
    @Test
    void reconciliationThatFindsNoDraftFailsTheOrder() {
        DemoHttpClient client = loggedIn("cust-a");
        Issued quote = quote(client, 2);
        erp.failOrders(MockErpOrderFault.TIMEOUT_BEFORE_COMMIT);
        assertThat(confirm(client, quote).body()).contains("\"status\":\"ORDER_OUTCOME_UNKNOWN\"");
        erp.failOrders(MockErpOrderFault.NONE);
        DemoHttpClient sales = loggedIn("sales-1");
        String id = number(sales.get("/api/internal/orders/unknown").body(), "requestId");

        assertThat(sales.postJson("/api/internal/orders/" + id + "/reconcile", "{\"outcome\":\"ABSENT\"}").body())
                .contains("\"status\":\"RECONCILED_ABSENT\"");

        assertThat(statuses(quote.quoteRef())).containsExactly("ORDER_FAILED");
        HttpResponse<String> after = confirm(client, quote);
        assertThat(after.body()).contains("\"status\":\"ORDER_FAILED\"").contains("\"handedOff\":true");
        assertThat(erp.createCalls()).isEqualTo(1);
        assertThat(erp.draftCount()).isZero();
        assertThat(handoffs(quote.conversation())).isEqualTo(1);
    }

    /** The ERP refuses the order: failed, handed to staff, and its message (with sentinels) goes nowhere. */
    @Test
    void anErpRejectionFailsTheOrderAndHandsOff(CapturedOutput output) {
        DemoHttpClient client = loggedIn("cust-a");
        Issued quote = quote(client, 2);
        erp.failOrders(MockErpOrderFault.REJECT);

        HttpResponse<String> failed = confirm(client, quote);

        assertThat(failed.statusCode()).as(failed.body()).isEqualTo(200);
        assertThat(failed.body()).contains("\"status\":\"ORDER_FAILED\"").contains("\"handedOff\":true")
                .doesNotContain("123456789").doesNotContain("CREDIT").doesNotContain("ZZHoney");
        assertThat(statuses(quote.quoteRef())).containsExactly("ORDER_FAILED");
        assertThat(handoffReason(quote.conversation())).isEqualTo("ORDER_NOT_CREATED");
        erp.failOrders(MockErpOrderFault.NONE);
        assertThat(confirm(client, quote).body()).contains("\"status\":\"ORDER_FAILED\"");
        assertThat(erp.createCalls()).isEqualTo(1);
        assertThat(erp.draftCount()).isZero();
        assertThat(output.getAll()).doesNotContain("123456789").doesNotContain("ZZHoney");
    }

    /** ERP outage at the re-check: safe error and handoff, the quote untouched, nothing ordered. */
    @Test
    void anErpOutageAtTheRecheckIsASafeErrorAndAHandoff(CapturedOutput output) {
        DemoHttpClient client = loggedIn("cust-a");
        Issued quote = quote(client, 2);
        erp.failReads(MockErpFaultMode.ERROR_WITH_SENTINEL);

        HttpResponse<String> unavailable = confirm(client, quote);

        assertThat(unavailable.statusCode()).as(unavailable.body()).isEqualTo(503);
        assertThat(unavailable.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"").doesNotContain("987654321")
                .doesNotContain("ZZHoney");
        assertThat(statuses(quote.quoteRef())).containsExactly("ISSUED");
        assertThat(handoffReason(quote.conversation())).isEqualTo("CATALOG_UNAVAILABLE");
        assertThat(conversationStatus(quote.conversation())).isEqualTo("HANDOFF_PENDING");
        assertThat(requests()).isZero();
        assertThat(erp.createCalls()).isZero();
        assertThat(output.getAll()).doesNotContain("987654321").doesNotContain("ZZHoney");

        // Once the ERP answers again the customer's own control still works.
        erp.failReads(MockErpFaultMode.NONE);
        assertThat(confirm(client, quote).body()).contains("\"status\":\"DRAFT_ORDER_CREATED\"");
    }

    /** T-UC04-CHAT-OK (AC-04.5, BR-14, TH-13): neither a chat "ok" nor the model can confirm a quote. */
    @Test
    void chatAgreementAndModelProposalsNeverCreateAnOrder() {
        DemoHttpClient client = loggedIn("cust-a");
        Issued quote = quote(client, 2);

        script(text("Vui lòng bấm nút xác nhận trên báo giá."));
        for (String message : List.of("ok", "đồng ý, chốt đơn nhé", "Xác nhận " + quote.quoteRef() + " v1")) {
            assertThat(send(client, quote.conversation(), message).statusCode()).isEqualTo(200);
        }
        for (String tool : List.of("confirmQuote", "createDraftOrder", "approveOrder")) {
            script(toolCall(tool, "{\"quoteRef\":\"" + quote.quoteRef() + "\",\"version\":1}"), text("Đã ghi nhận."));
            send(client, quote.conversation(), "hãy đặt hàng giúp tôi");
        }

        assertThat(statuses(quote.quoteRef())).containsExactly("ISSUED");
        assertThat(requests()).isZero();
        assertThat(attempts()).isEmpty();
        assertThat(erp.createCalls()).isZero();
        for (String body : providerBodies()) {
            assertThat(body).doesNotContain(quote.token());
        }
    }

    /**
     * Security review 2026-11-19 (M1): the limit is reserved before the re-check, so parallel requests at
     * the limit cannot all pass it and fan out to the ERP.
     */
    @Test
    void parallelConfirmationsCannotPassTheAttemptLimit() throws Exception {
        DemoHttpClient first = loggedIn("cust-a");
        Issued quote = quote(first, 2);
        String digest = jdbc.queryForObject("select customer_digest from quote", String.class);
        for (int i = 0; i < 19; i++) {
            jdbc.update("insert into quote_confirmation (customer_digest, outcome, occurred_at) "
                    + "values (?, 'NOT_FOUND', ?)", digest, Timestamp.from(clock.instant()));
        }
        List<DemoHttpClient> clients = new ArrayList<>(List.of(first));
        for (int i = 1; i < 10; i++) {
            clients.add(loggedIn("cust-a"));
        }
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<HttpResponse<String>>> results = new ArrayList<>();
            for (DemoHttpClient client : clients) {
                Callable<HttpResponse<String>> call = () -> {
                    start.await();
                    return confirm(client, quote);
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            for (Future<HttpResponse<String>> result : results) {
                statuses.add(result.get().statusCode());
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).filteredOn(status -> status == 429).hasSize(9);
        assertThat(statuses).filteredOn(status -> status == 200).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from quote_confirmation", Integer.class)).isEqualTo(20);
        assertThat(erp.createCalls()).isEqualTo(1);
    }

    /** TH-26: confirmation attempts are limited per customer, so a token cannot be guessed at speed. */
    @Test
    void confirmationAttemptsAreRateLimitedPerCustomer() {
        DemoHttpClient client = loggedIn("cust-a");
        Issued quote = quote(client, 2);

        for (int i = 0; i < 20; i++) {
            assertThat(confirm(client, quote.quoteRef(), 1, "qc_AAAAAAAAAAAAAAAAAAAA%02d".formatted(i)).statusCode())
                    .isEqualTo(404);
        }
        HttpResponse<String> limited = confirm(client, quote);

        assertThat(limited.statusCode()).as(limited.body()).isEqualTo(429);
        assertThat(statuses(quote.quoteRef())).containsExactly("ISSUED");
        assertThat(erp.createCalls()).isZero();
        // Another customer is not affected, and the window slides.
        assertThat(confirm(loggedIn("cust-b"), quote).statusCode()).isEqualTo(404);
        clock.advance(Duration.ofMinutes(61));
        assertThat(confirm(loggedIn("cust-a"), quote).body()).contains("\"status\":\"DRAFT_ORDER_CREATED\"");
    }
}
