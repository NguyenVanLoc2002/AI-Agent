package com.secureerp.agent.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.DemoHttpClient;
import com.secureerp.agent.erp.internal.MockErpConnector;
import com.secureerp.agent.erp.internal.MockErpSimulation;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * EP-08a end to end over HTTP (UC-05, UC-06; RBAC_MATRIX; DATA_FLOW §3): handoff → staff inbox of the
 * customer's team → claim → staff messages → resolve, with PostgreSQL, the real security chain and the
 * Fake provider. T-UC05-LOCK, T-UC06-XTEAM, T-UC06-OPS, T-RBAC-MATRIX, T-RBAC-AFTER-RESOLVE,
 * T-SEC01-STAFF (at the Fake provider, not through the capture proxy).
 *
 * <p>Demo configuration: cust-a and cust-c are served by team-1 (sales-1), cust-b by team-2 (sales-2).
 */
@ExtendWith(OutputCaptureExtension.class)
class StaffInboxIT extends ChatEnvironment {

    private static final Pattern HANDOFF_ID = Pattern.compile("\"handoffId\":(\\d+)");
    private static final String FAKE_CONVERSATION = "cv_NoSuchConversationAAAA";
    private static final String QUOTE_OE = "04465-0K290";

    @Autowired
    MockErpConnector mockErp;

    /** A conversation of this customer that was handed to staff (the provider fails once). */
    private String handedOff(DemoHttpClient customer) {
        String conversation = createConversation(customer);
        providerStatus(500);
        HttpResponse<String> response = send(customer, conversation, "Tôi cần tư vấn má phanh");
        assertThat(response.body()).contains("\"handedOff\":true");
        script();
        return conversation;
    }

    private static String handoffId(String body) {
        Matcher matcher = HANDOFF_ID.matcher(body);
        assertThat(matcher.find()).as(body).isTrue();
        return matcher.group(1);
    }

    private String status(String conversation) {
        return jdbc.queryForObject("select status from conversation where conversation_id = ?", String.class,
                conversation);
    }

    private static String staffMessage(String text) {
        return "{\"text\":\"" + text + "\"}";
    }

    /** UC-05/UC-06 happy path with T-UC05-LOCK, T-UC06-XTEAM, T-UC06-OPS and T-RBAC-AFTER-RESOLVE. */
    @Test
    void aHandoffIsClaimedAnsweredAndResolvedByTheCustomersTeamOnly() {
        DemoHttpClient customer = loggedIn("cust-a");
        String conversation = handedOff(customer);
        String messages = "/api/internal/conversations/" + conversation + "/messages";
        assertThat(status(conversation)).isEqualTo("HANDOFF_PENDING");

        // T-UC05-LOCK: while nobody has claimed it, the customer gets the waiting text and no model call.
        int providerCalls = providerBodies().size();
        assertThat(send(customer, conversation, "Có ai không?").body()).contains("đang chờ nhân viên")
                .contains("\"handedOff\":true");
        assertThat(providerBodies()).hasSize(providerCalls);

        // The inbox: the customer's team sees it, another team does not (T-UC06-XTEAM).
        DemoHttpClient sales = loggedIn("sales-1");
        DemoHttpClient otherTeam = loggedIn("sales-2");
        String inbox = sales.get("/api/internal/handoffs").body();
        assertThat(inbox).contains("\"conversationId\":\"" + conversation + "\"")
                .contains("\"reasonCode\":\"PROVIDER_ERROR\"").contains("\"status\":\"OPEN\"").contains("\"mine\":false")
                .doesNotContain("cref_").doesNotContain("usr_").doesNotContain("SYNTH-CUST").doesNotContain("má phanh");
        String id = handoffId(inbox);
        assertThat(otherTeam.get("/api/internal/handoffs").body()).isEqualTo("[]");
        for (String action : List.of("claim", "resolve")) {
            HttpResponse<String> refused = otherTeam.postEmpty("/api/internal/handoffs/" + id + "/" + action);
            assertThat(refused.statusCode()).as(action).isEqualTo(404);
            assertThat(refused.body()).contains("\"code\":\"NOT_FOUND\"").doesNotContain(conversation);
        }
        // T-UC06-OPS: an administrator, a reviewer and a customer are not business readers of the inbox.
        for (String notSales : List.of("ops-1", "secrev-1", "cust-a")) {
            assertThat(loggedIn(notSales).get("/api/internal/handoffs").statusCode()).as(notSales).isEqualTo(403);
        }
        // Being in the team is not enough to read a conversation: it must be claimed first.
        assertThat(sales.get(messages).statusCode()).isEqualTo(404);
        assertThat(sales.postJson(messages, staffMessage("xin chào")).statusCode()).isEqualTo(404);

        HttpResponse<String> claimed = sales.postEmpty("/api/internal/handoffs/" + id + "/claim");
        assertThat(claimed.statusCode()).as(claimed.body()).isEqualTo(200);
        assertThat(claimed.body()).contains("\"status\":\"CLAIMED\"").contains("\"mine\":true");
        assertThat(sales.postEmpty("/api/internal/handoffs/" + id + "/claim").statusCode()).isEqualTo(200);
        assertThat(status(conversation)).isEqualTo("HUMAN_ACTIVE");

        // The holder reads and writes; the other team still gets 404.
        assertThat(sales.get(messages).body()).contains("Tôi cần tư vấn má phanh").contains("\"direction\":\"CUSTOMER\"");
        assertThat(sales.postJson(messages, staffMessage("Chào anh, em là nhân viên tư vấn")).statusCode())
                .isEqualTo(204);
        assertThat(otherTeam.get(messages).statusCode()).isEqualTo(404);
        assertThat(otherTeam.postJson(messages, staffMessage("x")).statusCode()).isEqualTo(404);
        assertThat(customer.get("/api/demo/conversations/" + conversation + "/messages").body())
                .contains("\"direction\":\"STAFF\"").contains("Chào anh, em là nhân viên tư vấn");

        // The customer's message reaches the staff member; the bot stays silent.
        assertThat(send(customer, conversation, "Em cần 2 bộ").body()).contains("Nhân viên tư vấn đã nhận")
                .contains("\"handedOff\":true");
        assertThat(providerBodies()).hasSize(providerCalls);
        assertThat(sales.get(messages).body()).contains("Em cần 2 bộ");

        HttpResponse<String> resolved = sales.postEmpty("/api/internal/handoffs/" + id + "/resolve");
        assertThat(resolved.statusCode()).as(resolved.body()).isEqualTo(200);
        assertThat(resolved.body()).contains("\"status\":\"RESOLVED\"").contains("\"mine\":false");
        assertThat(status(conversation)).isEqualTo("ACTIVE_BOT");
        assertThat(sales.get("/api/internal/handoffs").body()).isEqualTo("[]");

        // T-RBAC-AFTER-RESOLVE (A-15): access to the content ends at resolve.
        assertThat(sales.get(messages).statusCode()).isEqualTo(404);
        assertThat(sales.postJson(messages, staffMessage("còn đó không?")).statusCode()).isEqualTo(404);
        assertThat(sales.postEmpty("/api/internal/handoffs/" + id + "/claim").statusCode()).isEqualTo(409);

        // BR-22: the bot answers again only now.
        assertThat(send(customer, conversation, "Cảm ơn").body()).contains(DEFAULT_ANSWER)
                .contains("\"handedOff\":false");
        assertThat(providerBodies()).hasSize(providerCalls + 1);
        assertThat(jdbc.queryForObject("select claimed_by = resolved_by and claimed_by like 'usr\\_%' from handoff",
                Boolean.class)).isTrue();
    }

    /**
     * T-SEC01-STAFF (BR-36, TH-25): what a staff member typed, here a customer price sentinel, never
     * reaches the model, neither while they hold the conversation nor after they gave it back.
     */
    @Test
    void staffMessagesNeverReachTheModel(CapturedOutput output) {
        DemoHttpClient customer = loggedIn("cust-a");
        String conversation = handedOff(customer);
        DemoHttpClient sales = loggedIn("sales-1");
        String id = handoffId(sales.get("/api/internal/handoffs").body());
        sales.postEmpty("/api/internal/handoffs/" + id + "/claim");
        String staffText = "Giá cho anh là 555444333 đ một bộ, công nợ hiện tại 123456789, mã nội bộ STAFFNOTE77";
        assertThat(sales.postJson("/api/internal/conversations/" + conversation + "/messages",
                staffMessage(staffText)).statusCode()).isEqualTo(204);
        send(customer, conversation, "Vâng, em cảm ơn");
        sales.postEmpty("/api/internal/handoffs/" + id + "/resolve");

        int before = providerBodies().size();
        for (String text : List.of("Cho tôi hỏi thêm", "Nhân viên vừa báo giá bao nhiêu?", "Nhắc lại giúp tôi")) {
            assertThat(send(customer, conversation, text).statusCode()).isEqualTo(200);
        }

        assertThat(providerBodies()).hasSize(before + 3);
        assertNothingRestrictedLeftTheApplication();
        for (String body : providerBodies()) {
            assertThat(body).doesNotContain("STAFFNOTE77").doesNotContain("555444333").doesNotContain("Giá cho anh");
        }
        // The customer still reads it; logs never carry the message.
        assertThat(customer.get("/api/demo/conversations/" + conversation + "/messages").body()).contains("STAFFNOTE77");
        assertThat(output.getAll()).doesNotContain("STAFFNOTE77").doesNotContain("555444333");
    }

    /**
     * Reviews 2026-11-19 (security M1): the customer's quote confirmation is blocked while a staff member
     * already holds the conversation. The reason is not dropped: the held handoff shows it, and resolving
     * keeps the bot locked with a new handoff for it.
     */
    @Test
    void aBlockedConfirmationWhileStaffHoldTheConversationKeepsTheBotLockedAfterResolve() {
        MockErpSimulation erp = mockErp.simulation();
        erp.reset();
        DemoHttpClient customer = loggedIn("cust-a");
        String conversation = createConversation(customer);
        script(toolCall("searchVerifiedProduct", "{\"oe\":\"04465-0K290\"}"), quoteFirstCandidate(2),
                text("Báo giá đã được gửi bên dưới."));
        String quoted = send(customer, conversation, "Báo giá 2 bộ má phanh 04465-0K290").body();
        Matcher ref = Pattern.compile("\"quoteRef\":\"([^\"]+)\"").matcher(quoted);
        Matcher token = Pattern.compile("\"confirmationToken\":\"([^\"]+)\"").matcher(quoted);
        assertThat(ref.find() && token.find()).as(quoted).isTrue();
        providerStatus(500);
        assertThat(send(customer, conversation, "Cho tôi gặp nhân viên").body()).contains("\"handedOff\":true");
        script();
        DemoHttpClient sales = loggedIn("sales-1");
        String id = handoffId(sales.get("/api/internal/handoffs").body());
        sales.postEmpty("/api/internal/handoffs/" + id + "/claim");

        try {
            erp.stock("SKU-BRK-PAD-A", 1);
            assertThat(customer.postJson("/api/demo/quotes/" + ref.group(1) + "/confirm", "{\"version\":1,"
                    + "\"confirmationToken\":\"" + token.group(1) + "\"}").body())
                    .contains("\"status\":\"CONFIRMATION_BLOCKED\"");
        } finally {
            erp.reset();
        }
        assertThat(status(conversation)).isEqualTo("HUMAN_ACTIVE");
        assertThat(sales.get("/api/internal/handoffs").body()).contains("\"reasonCode\":\"PROVIDER_ERROR\"")
                .contains("\"followUpReasonCode\":\"QUOTE_NOT_ISSUED\"");

        assertThat(sales.postEmpty("/api/internal/handoffs/" + id + "/resolve").body())
                .contains("\"status\":\"RESOLVED\"");

        assertThat(status(conversation)).as("not handed back to the bot").isEqualTo("HANDOFF_PENDING");
        assertThat(sales.get("/api/internal/handoffs").body()).contains("\"reasonCode\":\"QUOTE_NOT_ISSUED\"")
                .contains("\"status\":\"OPEN\"").doesNotContain("\"handoffId\":" + id + ",");
        int providerCalls = providerBodies().size();
        assertThat(send(customer, conversation, "Sao rồi?").body()).contains("đang chờ nhân viên");
        assertThat(providerBodies()).hasSize(providerCalls);
        // EP-08b: a resolve that opens a follow-up does not lift the A-34 lock; resolving the follow-up does.
        assertThat(lifts(conversation)).isZero();
        String followUp = handoffId(sales.get("/api/internal/handoffs").body());
        sales.postEmpty("/api/internal/handoffs/" + followUp + "/claim");
        assertThat(sales.postEmpty("/api/internal/handoffs/" + followUp + "/resolve").statusCode()).isEqualTo(200);
        assertThat(status(conversation)).isEqualTo("ACTIVE_BOT");
        assertThat(lifts(conversation)).isEqualTo(1);
    }

    private int lifts(String conversation) {
        return jdbc.queryForObject("select count(*) from quote_lock_lift where conversation_id = ?", Integer.class,
                conversation);
    }

    /** Asks the Fake provider for a quote of {@code quantity} in this conversation; returns the chat answer. */
    private String requestQuote(DemoHttpClient customer, String conversation, int quantity) {
        script(toolCall("searchVerifiedProduct", "{\"oe\":\"" + QUOTE_OE + "\"}"), quoteFirstCandidate(quantity),
                text("Báo giá đã được gửi bên dưới."));
        String body = send(customer, conversation, "Báo giá " + quantity + " bộ má phanh " + QUOTE_OE).body();
        script();
        return body;
    }

    private String claimOnlyHandoff(DemoHttpClient sales) {
        String id = handoffId(sales.get("/api/internal/handoffs").body());
        assertThat(sales.postEmpty("/api/internal/handoffs/" + id + "/claim").statusCode()).isEqualTo(200);
        return id;
    }

    /**
     * EP-08b (RBAC_MATRIX "Xem quote (có giá)", decision 4A): the staff member holding the conversation sees
     * its newest quote with prices and no confirmation token; nobody else, and not after resolve (A-15).
     * Reading it arms no token, so the customer's card still confirms.
     */
    @Test
    void theHolderSeesTheConversationsQuoteWithoutAConfirmationToken(CapturedOutput output) {
        DemoHttpClient customer = loggedIn("cust-a");
        String conversation = createConversation(customer);
        String quoted = requestQuote(customer, conversation, 2);
        Matcher ref = Pattern.compile("\"quoteRef\":\"([^\"]+)\"").matcher(quoted);
        assertThat(ref.find()).as(quoted).isTrue();
        String tokenHash = jdbc.queryForObject("select confirm_token_hash from quote", String.class);
        providerStatus(500);
        assertThat(send(customer, conversation, "Cho tôi gặp nhân viên").body()).contains("\"handedOff\":true");
        script();
        String quote = "/api/internal/conversations/" + conversation + "/quote";
        DemoHttpClient sales = loggedIn("sales-1");
        DemoHttpClient otherTeam = loggedIn("sales-2");
        assertThat(sales.get(quote).statusCode()).as("not claimed yet").isEqualTo(404);

        String id = claimOnlyHandoff(sales);
        HttpResponse<String> view = sales.get(quote);

        assertThat(view.statusCode()).as(view.body()).isEqualTo(200);
        assertThat(view.body()).contains("\"quoteRef\":\"" + ref.group(1) + "\"").contains("\"status\":\"ISSUED\"")
                .contains("\"sku\":\"SKU-BRK-PAD-A\"").contains("\"unitPrice\":\"555444333\"")
                .contains("\"currency\":\"VND\"").contains("Giá chưa gồm VAT")
                .doesNotContain("confirmationToken").doesNotContain("qc_").doesNotContain("SYNTH-CUST")
                .doesNotContain("cref_").doesNotContain("reason");
        assertThat(jdbc.queryForObject("select confirm_token_hash from quote", String.class))
                .as("no token armed by the staff read").isEqualTo(tokenHash);
        assertThat(otherTeam.get(quote).statusCode()).isEqualTo(404);
        for (String notSales : List.of("cust-a", "ops-1", "secrev-1")) {
            assertThat(loggedIn(notSales).get(quote).statusCode()).as(notSales).isEqualTo(403);
        }

        sales.postEmpty("/api/internal/handoffs/" + id + "/resolve");
        assertThat(sales.get(quote).statusCode()).as("T-RBAC-AFTER-RESOLVE").isEqualTo(404);
        assertThat(output.getAll()).doesNotContain("555444333");
    }

    /** A held conversation without any quote is the same 404. */
    @Test
    void aHeldConversationWithoutAQuoteShowsNone() {
        DemoHttpClient customer = loggedIn("cust-a");
        String conversation = handedOff(customer);
        DemoHttpClient sales = loggedIn("sales-1");
        claimOnlyHandoff(sales);

        HttpResponse<String> view = sales.get("/api/internal/conversations/" + conversation + "/quote");

        assertThat(view.statusCode()).isEqualTo(404);
        assertThat(view.body()).contains("\"code\":\"NOT_FOUND\"");
    }

    /**
     * EP-08b (TOOL_CONTRACTS §3.4, decision 6A): requestHandoff is executed. The reason the model gave is
     * the handoff reason, the turn ends without a second model call and the customer gets the fixed text.
     */
    @Test
    void requestHandoffKeepsTheModelsReasonAndEndsTheTurn() {
        for (String reason : List.of("CUSTOMER_REQUEST", "UNSURE", "OUT_OF_SCOPE")) {
            DemoHttpClient customer = loggedIn("cust-a");
            String conversation = createConversation(customer);
            int before = providerBodies().size();
            script(toolCall("requestHandoff", "{\"reason\":\"" + reason + "\"}"), text("không được dùng"));

            HttpResponse<String> answer = send(customer, conversation, "Tôi muốn hỏi về bảo hành xe");

            assertThat(answer.statusCode()).isEqualTo(200);
            assertThat(answer.body()).as(reason).contains("\"handedOff\":true").doesNotContain("không được dùng");
            assertThat(providerBodies()).as("one model call").hasSize(before + 1);
            assertThat(handoffReason(conversation)).isEqualTo(reason);
            assertThat(status(conversation)).isEqualTo("HANDOFF_PENDING");
            assertThat(jdbc.queryForObject("select decision from tool_invocation where conversation_id = ? "
                    + "and tool_name = 'requestHandoff'", String.class, conversation)).isEqualTo("INVOKED");
            script();
        }
    }

    /** A reason outside the enum is a schema error, not a handoff with a free-text reason. */
    @Test
    void requestHandoffWithAnUnknownReasonIsRejected() {
        DemoHttpClient customer = loggedIn("cust-a");
        String conversation = createConversation(customer);
        script(toolCall("requestHandoff", "{\"reason\":\"APPROVE_ORDER\"}"), text("Tôi có thể giúp gì thêm?"));

        send(customer, conversation, "xin chào");

        assertThat(jdbc.queryForObject("select reason_code from tool_invocation where conversation_id = ? "
                + "and tool_name = 'requestHandoff'", String.class, conversation)).isEqualTo("TOOL_SCHEMA");
        assertThat(handoffs(conversation)).isZero();
    }

    /**
     * EP-08b (A-34 lift, decision 7A): after a business refusal the customer gets no automatic quote, in any
     * conversation, until staff resolve the conversation of the refusal; then quotes are issued again.
     * A refusal of another conversation still locks.
     */
    @Test
    void resolvingTheRefusedConversationLiftsTheQuoteLock() {
        MockErpSimulation erp = mockErp.simulation();
        erp.reset();
        DemoHttpClient customer = loggedIn("cust-a");
        String refused = createConversation(customer);
        try {
            erp.stock("SKU-BRK-PAD-A", 1);
            assertThat(requestQuote(customer, refused, 2)).contains("\"handedOff\":true");
        } finally {
            erp.reset();
        }
        assertThat(handoffReason(refused)).isEqualTo("QUOTE_NOT_ISSUED");
        // Locked for the customer: another conversation gets no quote either (no ERP call).
        String other = createConversation(customer);
        assertThat(requestQuote(customer, other, 2)).contains("\"handedOff\":true").doesNotContain("quoteRef");
        assertThat(jdbc.queryForObject("select reason_code from quote_request where conversation_id = ?",
                String.class, other)).isEqualTo("HANDED_OFF");

        // A business refusal in the other conversation too, before any resolve (written directly: the gate
        // stops a real one before the ERP), timed on the application clock like the real one.
        jdbc.update("insert into quote_request (conversation_id, outcome, reason_code, line_count, customer_digest, "
                + "occurred_at) select ?, 'NOT_QUOTED', 'CREDIT_NOT_ELIGIBLE', 1, customer_digest, "
                + "occurred_at - interval '1 millisecond' from quote_request where conversation_id = ? limit 1", other, refused);
        DemoHttpClient sales = loggedIn("sales-1");

        resolve(sales, refused);

        assertThat(lifts(refused)).isEqualTo(1);
        assertThat(lifts(other)).isZero();
        // The other conversation's refusal still locks the customer, here as well.
        assertThat(requestQuote(customer, refused, 2)).contains("\"handedOff\":true").doesNotContain("quoteRef");
        assertThat(lastQuoteReason(refused)).isEqualTo("HANDED_OFF");

        resolve(sales, refused);
        resolve(sales, other);

        assertThat(lifts(other)).isEqualTo(1);
        assertThat(requestQuote(customer, refused, 2)).contains("\"quoteRef\":\"").contains("\"handedOff\":false");

        // Reviews EP-08b (backend M1, security L-1): a refusal stamped before the lift but committed after it
        // (its request started before the resolve) is not covered by the lift and still locks.
        jdbc.update("insert into quote_request (conversation_id, outcome, reason_code, line_count, customer_digest, "
                + "occurred_at) select conversation_id, 'NOT_QUOTED', 'INSUFFICIENT_STOCK', 1, customer_digest, "
                + "(select min(lifted_at) from quote_lock_lift) - interval '1 millisecond' "
                + "from quote_request where conversation_id = ? limit 1", refused);
        assertThat(requestQuote(customer, other, 2)).contains("\"handedOff\":true").doesNotContain("quoteRef");
    }

    /** Claims and resolves the open handoff of this conversation. */
    private void resolve(DemoHttpClient sales, String conversation) {
        String handoff = String.valueOf(jdbc.queryForObject(
                "select handoff_id from handoff where conversation_id = ? and status = 'OPEN'", Long.class,
                conversation));
        assertThat(sales.postEmpty("/api/internal/handoffs/" + handoff + "/claim").statusCode()).isEqualTo(200);
        assertThat(sales.postEmpty("/api/internal/handoffs/" + handoff + "/resolve").statusCode()).isEqualTo(200);
    }

    private String lastQuoteReason(String conversation) {
        return jdbc.queryForObject("select reason_code from quote_request where conversation_id = ? "
                + "order by id desc limit 1", String.class, conversation);
    }

    /**
     * Review round 2 (2026-11-19): a conversation created before teams existed has no stored team. Its
     * handoff goes to the team serving the customer now, not into a queue nobody sees.
     */
    @Test
    void aConversationFromBeforeTeamsIsHandedToTheTeamServingItsCustomer() {
        DemoHttpClient customer = loggedIn("cust-a");
        String conversation = createConversation(customer);
        jdbc.update("update conversation set team = null where conversation_id = ?", conversation);
        providerStatus(500);
        assertThat(send(customer, conversation, "xin chào").body()).contains("\"handedOff\":true");

        assertThat(jdbc.queryForObject("select team from handoff where conversation_id = ?", String.class,
                conversation)).isEqualTo("team-1");
        assertThat(loggedIn("sales-1").get("/api/internal/handoffs").body()).contains(conversation);
        assertThat(loggedIn("sales-2").get("/api/internal/handoffs").body()).isEqualTo("[]");
    }

    /**
     * T-RBAC-MATRIX: every endpoint against every role. A function outside the role is 403, a resource
     * that is not the caller's (here: one that does not exist) is 404, an anonymous caller is 401.
     */
    @Test
    void everyEndpointAnswersEveryRoleAsTheMatrixSays() {
        record Call(String method, String path, String body, int customer, int sales, int reviewer) {

            Call(String method, String path, String body, int customer, int sales) {
                this(method, path, body, customer, sales, 403);
            }
        }
        String confirm = "{\"version\":1,\"confirmationToken\":\"qc_AAAAAAAAAAAAAAAAAAAAAA\"}";
        List<Call> matrix = List.of(
                new Call("POST", "/api/demo/conversations", "", 201, 403),
                new Call("GET", "/api/demo/conversations/" + FAKE_CONVERSATION + "/messages", null, 404, 403),
                new Call("POST", "/api/demo/conversations/" + FAKE_CONVERSATION + "/messages",
                        "{\"clientEventId\":\"7b0e0c7e-2f3a-4f6f-9d0a-000000000001\",\"text\":\"x\"}", 404, 403),
                new Call("GET", "/api/demo/quotes/BG-ZZZZZZZZ", null, 404, 403),
                new Call("POST", "/api/demo/quotes/BG-ZZZZZZZZ/confirm", confirm, 404, 403),
                new Call("GET", "/api/internal/handoffs", null, 403, 200),
                new Call("POST", "/api/internal/handoffs/999999/claim", "", 403, 404),
                new Call("POST", "/api/internal/handoffs/999999/resolve", "", 403, 404),
                new Call("GET", "/api/internal/conversations/" + FAKE_CONVERSATION + "/messages", null, 403, 404),
                new Call("POST", "/api/internal/conversations/" + FAKE_CONVERSATION + "/messages", staffMessage("x"),
                        403, 404),
                new Call("GET", "/api/internal/orders/unknown", null, 403, 200),
                new Call("POST", "/api/internal/orders/999999/reconcile", "{\"outcome\":\"ABSENT\"}", 403, 404),
                // EP-08b
                new Call("GET", "/api/internal/conversations/" + FAKE_CONVERSATION + "/quote", null, 403, 404),
                new Call("GET", "/api/internal/orders/awaiting-decision", null, 403, 200),
                new Call("POST", "/api/internal/orders/999999/staff-decision", "{\"decision\":\"APPROVED\"}", 403,
                        404),
                // EP-09b (T-UC07-RBAC): the security inspector is SECREV only
                new Call("GET", "/api/demo/security/traces/7b0e0c7e-2f3a-4f6f-9d0a-000000000001", null, 403, 403,
                        404),
                new Call("GET", "/api/demo/security/events", null, 403, 403, 200));
        DemoHttpClient anonymous = client();
        anonymous.csrf();
        for (Call call : matrix) {
            assertThat(invoke(anonymous, call.method(), call.path(), call.body()).statusCode())
                    .as("anonymous %s %s", call.method(), call.path()).isEqualTo(401);
        }
        for (String user : List.of("cust-a", "sales-1", "ops-1", "secrev-1")) {
            DemoHttpClient client = loggedIn(user);
            for (Call call : matrix) {
                int expected = switch (user) {
                    case "cust-a" -> call.customer();
                    case "sales-1" -> call.sales();
                    case "secrev-1" -> call.reviewer();
                    default -> 403; // OPERATOR has no business endpoint here (BR-24)
                };
                HttpResponse<String> response = invoke(client, call.method(), call.path(), call.body());
                assertThat(response.statusCode()).as("%s %s %s: %s", user, call.method(), call.path(), response.body())
                        .isEqualTo(expected);
            }
        }
    }

    private static HttpResponse<String> invoke(DemoHttpClient client, String method, String path, String body) {
        if ("GET".equals(method)) {
            return client.get(path);
        }
        return body == null || body.isEmpty() ? client.postJson(path, "") : client.postJson(path, body);
    }
}
