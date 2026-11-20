package com.secureerp.agent.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.DemoHttpClient;
import com.secureerp.agent.conversation.ChatEnvironment;
import com.secureerp.agent.erp.internal.MockErpConnector;
import com.secureerp.agent.erp.internal.MockErpOrderFault;
import com.secureerp.agent.erp.internal.MockErpSimulation;
import java.net.http.HttpResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * EP-08b staff decision on created draft orders over HTTP (ADR-007 STAFF_APPROVED / STAFF_REJECTED;
 * RBAC_MATRIX "Ghi nhận quyết định duyệt/từ chối đơn"; A-15 manual recording; owner decisions
 * 2026-11-20, A-43): SALES of the customer's team only, final, no free text, the quote follows in the
 * same transaction and the customer sees the new status.
 *
 * <p>Demo configuration: cust-a is served by team-1 (sales-1); sales-2 is team-2.
 */
@ExtendWith(OutputCaptureExtension.class)
class StaffDecisionIT extends ChatEnvironment {

    private static final String OE = "04465-0K290";
    private static final String PRICE_A = "555444333";
    private static final String ERP_CUSTOMER_A = "SYNTH-CUST-A";
    private static final String AWAITING = "/api/internal/orders/awaiting-decision";
    private static final String APPROVE = "{\"decision\":\"APPROVED\"}";
    private static final String REJECT = "{\"decision\":\"REJECTED\"}";

    @Autowired
    MockErpConnector mockErp;

    private MockErpSimulation erp;

    @BeforeEach
    void resetErp() {
        erp = mockErp.simulation();
        erp.reset();
    }

    private record Confirmed(String conversation, String quoteRef, String requestId) {
    }

    /** A quote of cust-a confirmed by the customer; the draft order is created unless the ERP fails. */
    private Confirmed confirmedQuote(DemoHttpClient customer) {
        String conversation = createConversation(customer);
        script(toolCall("searchVerifiedProduct", "{\"oe\":\"" + OE + "\"}"), quoteFirstCandidate(2),
                text("Báo giá đã được gửi bên dưới."));
        String quoted = send(customer, conversation, "Báo giá 2 bộ má phanh " + OE).body();
        String quoteRef = field(quoted, "quoteRef");
        HttpResponse<String> confirmed = customer.postJson("/api/demo/quotes/" + quoteRef + "/confirm",
                "{\"version\":1,\"confirmationToken\":\"" + field(quoted, "confirmationToken") + "\"}");
        assertThat(confirmed.statusCode()).as(confirmed.body()).isIn(200, 202);
        String requestId = String.valueOf(jdbc.queryForObject(
                "select id from draft_order_request where quote_ref = ?", Long.class, quoteRef));
        return new Confirmed(conversation, quoteRef, requestId);
    }

    private static String decision(String requestId) {
        return "/api/internal/orders/" + requestId + "/staff-decision";
    }

    private static String field(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\":\"([^\"]+)\"").matcher(json);
        assertThat(matcher.find()).as("%s in %s", name, json).isTrue();
        return matcher.group(1);
    }

    private String requestStatus(String requestId) {
        return jdbc.queryForObject("select status from draft_order_request where id = ?", String.class,
                Long.parseLong(requestId));
    }

    private String quoteStatus(String quoteRef) {
        return jdbc.queryForObject("select status from quote where public_ref = ?", String.class, quoteRef);
    }

    private int decisionEvents() {
        return jdbc.queryForObject("select count(*) from draft_order_event where event like 'STAFF\\_%'",
                Integer.class);
    }

    /** Happy path, RBAC, team scope, idempotent repeat, final decision, customer view, no ERP call. */
    @Test
    void theCustomersTeamApprovesACreatedDraftAndTheQuoteFollows(CapturedOutput output) {
        DemoHttpClient customer = loggedIn("cust-a");
        Confirmed order = confirmedQuote(customer);
        assertThat(requestStatus(order.requestId())).isEqualTo("CREATED");
        DemoHttpClient sales = loggedIn("sales-1");
        DemoHttpClient otherTeam = loggedIn("sales-2");

        String awaiting = sales.get(AWAITING).body();
        assertThat(awaiting).contains("\"requestId\":" + order.requestId())
                .contains("\"quoteRef\":\"" + order.quoteRef() + "\"").contains("\"erpOrderRef\":\"MOCK-DO-")
                .doesNotContain(PRICE_A).doesNotContain(ERP_CUSTOMER_A).doesNotContain("cref_").doesNotContain("usr_");
        assertThat(otherTeam.get(AWAITING).body()).isEqualTo("[]");
        assertThat(otherTeam.postJson(decision(order.requestId()), APPROVE).statusCode()).isEqualTo(404);
        for (String notSales : List.of("cust-a", "ops-1", "secrev-1")) {
            DemoHttpClient client = loggedIn(notSales);
            assertThat(client.get(AWAITING).statusCode()).as(notSales).isEqualTo(403);
            assertThat(client.postJson(decision(order.requestId()), APPROVE).statusCode()).as(notSales).isEqualTo(403);
        }
        assertThat(sales.postJson(decision(order.requestId()), "{\"decision\":\"MAYBE\"}").statusCode()).isEqualTo(400);
        assertThat(sales.postJson(decision(order.requestId()), "{}").statusCode()).isEqualTo(400);
        assertThat(requestStatus(order.requestId())).isEqualTo("CREATED");

        HttpResponse<String> approved = sales.postJson(decision(order.requestId()), APPROVE);

        assertThat(approved.statusCode()).as(approved.body()).isEqualTo(200);
        assertThat(approved.body()).contains("\"status\":\"STAFF_APPROVED\"");
        assertThat(requestStatus(order.requestId())).isEqualTo("STAFF_APPROVED");
        assertThat(quoteStatus(order.quoteRef())).isEqualTo("STAFF_APPROVED");
        assertThat(jdbc.queryForObject("select erp_order_ref like 'MOCK-DO-%' from draft_order_request",
                Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from draft_order_event where event = 'STAFF_APPROVED' "
                + "and actor like 'usr\\_%'", Integer.class)).isEqualTo(1);
        // Repeat: same answer, nothing new; the opposite decision is refused (final).
        assertThat(sales.postJson(decision(order.requestId()), APPROVE).body()).contains("\"status\":\"STAFF_APPROVED\"");
        assertThat(sales.postJson(decision(order.requestId()), REJECT).statusCode()).isEqualTo(409);
        assertThat(decisionEvents()).isEqualTo(1);
        assertThat(sales.get(AWAITING).body()).isEqualTo("[]");

        // The customer sees the status (decision 3A: no message), a repeated confirmation changes nothing.
        assertThat(customer.get("/api/demo/quotes/" + order.quoteRef()).body())
                .contains("\"status\":\"STAFF_APPROVED\"").doesNotContain("confirmationToken");
        assertThat(erp.createCalls()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from handoff", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select status from conversation where conversation_id = ?", String.class,
                order.conversation())).isEqualTo("ACTIVE_BOT");
        assertThat(output.getAll()).doesNotContain(PRICE_A).doesNotContain(ERP_CUSTOMER_A);
    }

    @Test
    void aRejectionIsFinalAndTheQuoteShowsRejected() {
        DemoHttpClient customer = loggedIn("cust-a");
        Confirmed order = confirmedQuote(customer);
        DemoHttpClient sales = loggedIn("sales-1");

        assertThat(sales.postJson(decision(order.requestId()), REJECT).body()).contains("\"status\":\"STAFF_REJECTED\"");

        assertThat(quoteStatus(order.quoteRef())).isEqualTo("REJECTED");
        assertThat(customer.get("/api/demo/quotes/" + order.quoteRef()).body()).contains("\"status\":\"REJECTED\"");
        assertThat(sales.postJson(decision(order.requestId()), APPROVE).statusCode()).isEqualTo(409);
        assertThat(requestStatus(order.requestId())).isEqualTo("STAFF_REJECTED");
    }

    /** Only a created draft waits for a decision: an unknown outcome must be reconciled first. */
    @Test
    void anUnknownOutcomeIsReconciledBeforeItCanBeDecided() {
        DemoHttpClient customer = loggedIn("cust-a");
        erp.failOrders(MockErpOrderFault.TIMEOUT_AFTER_COMMIT);
        Confirmed order = confirmedQuote(customer);
        erp.failOrders(MockErpOrderFault.NONE);
        assertThat(requestStatus(order.requestId())).isEqualTo("OUTCOME_UNKNOWN");
        DemoHttpClient sales = loggedIn("sales-1");

        assertThat(sales.get(AWAITING).body()).isEqualTo("[]");
        assertThat(sales.postJson(decision(order.requestId()), APPROVE).statusCode()).isEqualTo(409);
        assertThat(requestStatus(order.requestId())).isEqualTo("OUTCOME_UNKNOWN");

        assertThat(sales.postJson("/api/internal/orders/" + order.requestId() + "/reconcile",
                "{\"outcome\":\"CREATED\",\"erpOrderRef\":\"MOCK-DO-000001\"}").statusCode()).isEqualTo(200);
        assertThat(sales.get(AWAITING).body()).contains("\"requestId\":" + order.requestId());
        assertThat(sales.postJson(decision(order.requestId()), APPROVE).body()).contains("\"status\":\"STAFF_APPROVED\"");

        assertThat(quoteStatus(order.quoteRef())).isEqualTo("STAFF_APPROVED");
        // Reconciliation is over once decided.
        assertThat(sales.postJson("/api/internal/orders/" + order.requestId() + "/reconcile",
                "{\"outcome\":\"ABSENT\"}").statusCode()).isEqualTo(409);
        // EP-09b (A-41(o), A-43(n)): each change is one audit event committed with it; the refused ones are not.
        assertThat(jdbc.queryForList("select action || ':' || outcome || ':' || actor_role from audit_event "
                + "where action in ('ORDER_RECONCILED', 'STAFF_ORDER_DECIDED') order by id", String.class))
                .containsExactly("ORDER_RECONCILED:RECONCILED_CREATED:SALES", "STAFF_ORDER_DECIDED:STAFF_APPROVED:SALES");
    }

    /**
     * EP-09b reviews (M-1, M3): a decision and a reconciliation are rolled back without their audit record; a
     * request of another team is a denial recorded after the transaction, with no second connection inside it.
     */
    @Test
    void aDecisionOrReconciliationIsNotStoredWithoutItsAuditRecord(CapturedOutput output) {
        DemoHttpClient customer = loggedIn("cust-a");
        Confirmed created = confirmedQuote(customer);
        DemoHttpClient sales = loggedIn("sales-1");
        assertThat(loggedIn("sales-2").postJson(decision(created.requestId()), APPROVE).statusCode()).isEqualTo(404);
        jdbc.execute("alter table audit_event rename to audit_event_moved");
        try {
            assertThat(sales.postJson(decision(created.requestId()), APPROVE).statusCode())
                    .isGreaterThanOrEqualTo(500);
        } finally {
            jdbc.execute("alter table audit_event_moved rename to audit_event");
        }
        assertThat(requestStatus(created.requestId())).isEqualTo("CREATED");
        assertThat(decisionEvents()).isZero();

        erp.failOrders(MockErpOrderFault.TIMEOUT_AFTER_COMMIT);
        Confirmed unknown = confirmedQuote(customer);
        erp.failOrders(MockErpOrderFault.NONE);
        String reconcile = "/api/internal/orders/" + unknown.requestId() + "/reconcile";
        assertThat(loggedIn("sales-2").postJson(reconcile, "{\"outcome\":\"ABSENT\"}").statusCode())
                .isEqualTo(404);
        jdbc.execute("alter table audit_event rename to audit_event_moved");
        try {
            assertThat(sales.postJson(reconcile, "{\"outcome\":\"ABSENT\"}").statusCode())
                    .isGreaterThanOrEqualTo(500);
        } finally {
            jdbc.execute("alter table audit_event_moved rename to audit_event");
        }
        assertThat(requestStatus(unknown.requestId())).isEqualTo("OUTCOME_UNKNOWN");
        assertThat(jdbc.queryForList("select action || ':' || resource_type || ':' || outcome from audit_event "
                + "where action = 'ACCESS_DENIED' order by id", String.class))
                .containsExactly("ACCESS_DENIED:DRAFT_ORDER:FORBIDDEN_RESOURCE",
                        "ACCESS_DENIED:DRAFT_ORDER:FORBIDDEN_RESOURCE");
        assertThat(output.getAll()).doesNotContain("separate_write_inside_transaction");
    }

    /** Ten staff sessions decide at once, five each way: exactly one decision is recorded. */
    @Test
    void parallelDecisionsRecordExactlyOne() throws Exception {
        DemoHttpClient customer = loggedIn("cust-a");
        Confirmed order = confirmedQuote(customer);
        List<DemoHttpClient> staff = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            staff.add(loggedIn("sales-1"));
        }
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> answers = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 10; i++) {
                DemoHttpClient client = staff.get(i);
                String body = i % 2 == 0 ? APPROVE : REJECT;
                Callable<HttpResponse<String>> call = () -> {
                    start.await();
                    return client.postJson(decision(order.requestId()), body);
                };
                answers.add(pool.submit(call));
            }
            start.countDown();
            String winner = null;
            int ok = 0;
            for (Future<HttpResponse<String>> answer : answers) {
                HttpResponse<String> response = answer.get();
                assertThat(response.statusCode()).as(response.body()).isIn(200, 409);
                if (response.statusCode() == 200) {
                    ok++;
                    String status = field(response.body(), "status");
                    assertThat(winner == null || winner.equals(status)).as("one decision wins").isTrue();
                    winner = status;
                }
            }
            assertThat(ok).isEqualTo(5);
            assertThat(requestStatus(order.requestId())).isEqualTo(winner);
        }
        assertThat(decisionEvents()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'STAFF_ORDER_DECIDED'",
                Integer.class)).as("one audit event for the one decision").isEqualTo(1);
        assertThat(quoteStatus(order.quoteRef())).isIn("STAFF_APPROVED", "REJECTED");
    }

    /**
     * Backend review EP-08b M2: the request is created but the quote still shows the order as being sent or
     * unknown (an earlier mirror failed and was only logged). The decision catches the quote up instead of
     * leaving a request that is listed but can never be decided.
     */
    @Test
    void aQuoteLeftBehindByTheOrderIsCaughtUpByTheDecision() {
        DemoHttpClient customer = loggedIn("cust-a");
        DemoHttpClient sales = loggedIn("sales-1");
        for (String behind : List.of("ORDER_SUBMITTING", "ORDER_OUTCOME_UNKNOWN")) {
            Confirmed order = confirmedQuote(customer);
            jdbc.update("update quote set status = ? where public_ref = ?", behind, order.quoteRef());

            HttpResponse<String> approved = sales.postJson(decision(order.requestId()), APPROVE);

            assertThat(approved.statusCode()).as(behind + ": " + approved.body()).isEqualTo(200);
            assertThat(quoteStatus(order.quoteRef())).as(behind).isEqualTo("STAFF_APPROVED");
        }
        // The catch-up is a quiet repair: it opens no handoff (review round 2, I-3).
        assertThat(jdbc.queryForObject("select count(*) from handoff", Integer.class)).isZero();
    }

    /** A decision needs its quote (rule 10, fail closed): without one it is refused and nothing is kept. */
    @Test
    void aDecisionWithoutItsQuoteIsRefused() {
        DemoHttpClient customer = loggedIn("cust-a");
        Confirmed order = confirmedQuote(customer);
        jdbc.update("update quote set version = 9 where public_ref = ?", order.quoteRef());

        assertThat(loggedIn("sales-1").postJson(decision(order.requestId()), REJECT).statusCode()).isEqualTo(409);

        assertThat(requestStatus(order.requestId())).isEqualTo("CREATED");
        assertThat(decisionEvents()).isZero();
    }

    /** A quote that cannot follow (here: moved on outside the flow) rolls the decision back: 409, nothing kept. */
    @Test
    void aQuoteThatCannotFollowRollsTheDecisionBack() {
        DemoHttpClient customer = loggedIn("cust-a");
        Confirmed order = confirmedQuote(customer);
        jdbc.update("update quote set status = 'ORDER_FAILED' where public_ref = ?", order.quoteRef());

        assertThat(loggedIn("sales-1").postJson(decision(order.requestId()), APPROVE).statusCode()).isEqualTo(409);

        assertThat(requestStatus(order.requestId())).isEqualTo("CREATED");
        assertThat(decisionEvents()).isZero();
    }
}
