package com.secureerp.agent.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.DemoHttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * EP-09a over HTTP (UC-08, ADR-013 ACCEPTED 2026-11-20, A-44): the kill switch (T-SEC06, AC-08.1) and the
 * provider-auth breaker (T-SEC07, AC-08.2), with PostgreSQL, the real security chain and the Fake
 * provider. "No provider call" is counted at the Fake provider.
 */
@ExtendWith(OutputCaptureExtension.class)
class KillSwitchIT extends ChatEnvironment {

    private static final String SWITCH = "/api/ops/kill-switch";
    private static final String OFF = "{\"enabled\":false}";
    private static final String ON = "{\"enabled\":true}";

    private String handoffReason(String conversation, int index) {
        return jdbc.queryForList("select reason_code from handoff where conversation_id = ? order by handoff_id",
                String.class, conversation).get(index);
    }

    private int switchEvents(String reason, boolean operator) {
        return jdbc.queryForObject("select count(*) from feature_switch_event where coalesce(reason, 'ON') = ? and "
                + (operator ? "actor like 'usr\\_%'" : "actor is null"), Integer.class, reason);
    }

    /** RBAC_MATRIX "Bật/tắt kill switch": OPS only, at the URL and in the service. */
    @Test
    void onlyAnOperatorSeesOrChangesTheSwitch() {
        for (String user : List.of("cust-a", "sales-1", "secrev-1")) {
            DemoHttpClient client = loggedIn(user);
            assertThat(client.get(SWITCH).statusCode()).as(user).isEqualTo(403);
            assertThat(client.postJson(SWITCH, OFF).statusCode()).as(user).isEqualTo(403);
        }
        DemoHttpClient anonymous = client();
        anonymous.csrf();
        assertThat(anonymous.get(SWITCH).statusCode()).isEqualTo(401);
        assertThat(anonymous.postJson(SWITCH, OFF).statusCode()).isEqualTo(401);
        DemoHttpClient ops = loggedIn("ops-1");
        assertThat(ops.postJson(SWITCH, "{}").statusCode()).isEqualTo(400);
        assertThat(ops.postJson(SWITCH, "{\"enabled\":\"maybe\"}").statusCode()).isEqualTo(400);

        assertThat(ops.get(SWITCH).body()).contains("\"aiEnabled\":true").contains("\"reason\":null");
        assertThat(killSwitch.isAiEnabled()).isTrue();
        assertThat(switchEvents("OPERATOR", true)).isZero();
    }

    /**
     * T-SEC06 (AC-08.1): after the operator switches the AI off, nine conversations of three customers (the
     * per-customer limit is 5 new conversations an hour) sending at once are all handed to staff
     * (AI_DISABLED) and the provider gets nothing. Switching it on again restores the bot.
     */
    @Test
    void switchedOffEveryNewTurnGoesToStaffWithoutAProviderCall(CapturedOutput output) throws Exception {
        DemoHttpClient ops = loggedIn("ops-1");
        List<DemoHttpClient> customers = new ArrayList<>();
        List<String> conversations = new ArrayList<>();
        List<String> users = List.of("cust-a", "cust-b", "cust-c");
        for (int i = 0; i < 9; i++) {
            DemoHttpClient customer = loggedIn(users.get(i % 3));
            customers.add(customer);
            conversations.add(createConversation(customer));
        }

        HttpResponse<String> off = ops.postJson(SWITCH, OFF);

        assertThat(off.statusCode()).as(off.body()).isEqualTo(200);
        assertThat(off.body()).contains("\"aiEnabled\":false").contains("\"reason\":\"OPERATOR\"");
        int before = providerBodies().size();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> answers = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(9)) {
            for (int i = 0; i < 9; i++) {
                DemoHttpClient customer = customers.get(i);
                String conversation = conversations.get(i);
                Callable<HttpResponse<String>> call = () -> {
                    start.await();
                    return send(customer, conversation, "Tôi cần má phanh cho xe");
                };
                answers.add(pool.submit(call));
            }
            start.countDown();
            for (Future<HttpResponse<String>> answer : answers) {
                HttpResponse<String> response = answer.get();
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).contains("\"handedOff\":true").doesNotContain(DEFAULT_ANSWER);
            }
        }
        assertThat(providerBodies()).as("0 provider calls while off").hasSize(before);
        for (String conversation : conversations) {
            assertThat(handoffReason(conversation, 0)).isEqualTo("AI_DISABLED");
        }
        assertThat(switchEvents("OPERATOR", true)).isEqualTo(1);
        // DATA_FLOW step 2: stopped at intake, before the agent, the tools and the gateway.
        assertThat(output.getAll()).contains("conversation.turn ai_disabled").doesNotContain("agent.turn handoff");
        assertThat(jdbc.queryForObject("select count(*) from tool_invocation", Integer.class)).isZero();

        assertThat(ops.postJson(SWITCH, ON).body()).contains("\"aiEnabled\":true");
        DemoHttpClient customer = loggedIn("cust-a");
        assertThat(send(customer, createConversation(customer), "xin chào").body()).contains(DEFAULT_ANSWER);
        assertThat(providerBodies()).hasSize(before + 1);
        assertThat(switchEvents("ON", true)).isEqualTo(1);
    }

    /**
     * DLP-OFF (A-51, owner 2026-11-23; A-44(r), A-45(k)): while the AI is off, or the provider-auth breaker is
     * open, a message the inbound sanitizer blocked still hands off as DLP_INBOUND, so staff keep the DLP
     * signal; a clean message still hands off as AI_DISABLED / PROVIDER_AUTH. Nothing runs for either: no
     * provider call, no VIN intake, no tool, and the blocked value is not stored.
     */
    @Test
    void aBlockedMessageKeepsItsDlpReasonWhileTheAiIsOff(CapturedOutput output) {
        String blockedText = "giá vốn là 987654321, xe VIN 5ZZAPAS11RA000001";
        DemoHttpClient ops = loggedIn("ops-1");
        DemoHttpClient customer = loggedIn("cust-a");
        String blocked = createConversation(customer);
        String clean = createConversation(customer);
        assertThat(ops.postJson(SWITCH, OFF).statusCode()).isEqualTo(200);
        int before = providerBodies().size();

        assertThat(send(customer, blocked, blockedText).body()).contains("\"handedOff\":true")
                .doesNotContain("987654321");
        assertThat(send(customer, clean, "Tôi cần má phanh cho xe").body()).contains("\"handedOff\":true");

        assertThat(handoffReason(blocked, 0)).isEqualTo("DLP_INBOUND");
        assertThat(handoffReason(clean, 0)).isEqualTo("AI_DISABLED");
        assertThat(providerBodies()).as("0 provider calls while off").hasSize(before);
        assertThat(jdbc.queryForObject("select count(*) from conversation_vin", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from tool_invocation", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select text from message where conversation_id = ? and direction = 'CUSTOMER'",
                String.class, blocked)).singleElement().satisfies(text ->
                assertThat(text).doesNotContain("987654321").doesNotContain("5ZZAPAS11RA000001"));
        assertThat(output.getAll()).contains("conversation.turn inbound_blocked").doesNotContain("987654321");

        // The breaker open (a revoked key): the same order.
        assertThat(ops.postJson(SWITCH, ON).statusCode()).isEqualTo(200);
        providerStatus(401);
        String tripping = createConversation(customer);
        assertThat(send(customer, tripping, "xin chào").body()).contains("\"handedOff\":true");
        assertThat(handoffReason(tripping, 0)).isEqualTo("PROVIDER_AUTH");
        int calls = providerBodies().size();
        String blockedWhileOpen = createConversation(customer);
        String cleanWhileOpen = createConversation(customer);

        send(customer, blockedWhileOpen, blockedText);
        send(customer, cleanWhileOpen, "xin chào");

        assertThat(handoffReason(blockedWhileOpen, 0)).isEqualTo("DLP_INBOUND");
        assertThat(handoffReason(cleanWhileOpen, 0)).isEqualTo("PROVIDER_AUTH");
        assertThat(providerBodies()).hasSize(calls);

        // An unreadable switch (fail closed, ADR-013) with a blocked message: the same (security review L).
        // Another customer: cust-a has used its 5 new conversations of the hour.
        assertThat(ops.postJson(SWITCH, ON).statusCode()).isEqualTo(200);
        customer = loggedIn("cust-b");
        String blockedWhileUnreadable = createConversation(customer);
        jdbc.execute("alter table feature_switch rename to feature_switch_moved");
        try {
            clock.advance(Duration.ofSeconds(31));
            String cleanWhileUnreadable = createConversation(customer);
            send(customer, cleanWhileUnreadable, "xin chào");
            assertThat(handoffReason(cleanWhileUnreadable, 0)).isEqualTo("AI_DISABLED");
            send(customer, blockedWhileUnreadable, blockedText);
        } finally {
            jdbc.execute("alter table feature_switch_moved rename to feature_switch");
        }
        assertThat(handoffReason(blockedWhileUnreadable, 0)).isEqualTo("DLP_INBOUND");
        assertThat(providerBodies()).hasSize(calls);
    }

    /**
     * T-SEC06, other instance and database variants (ADR-013): a change written by another instance takes
     * effect here within the 30 s cache; a switch whose state cannot be read is off (fail closed).
     */
    @Test
    void anotherInstancesChangeArrivesWithinThirtySecondsAndAnUnreadableSwitchIsOff() {
        DemoHttpClient customer = loggedIn("cust-a");
        String conversation = createConversation(customer);
        assertThat(killSwitch.isAiEnabled()).isTrue(); // cached now
        jdbc.update("update feature_switch set enabled = false, reason = 'OPERATOR' where name = 'ai_assistant'");

        clock.advance(Duration.ofSeconds(30));
        int before = providerBodies().size();
        assertThat(send(customer, conversation, "xin chào").body()).contains("\"handedOff\":true");
        assertThat(providerBodies()).hasSize(before);
        assertThat(handoffReason(conversation, 0)).isEqualTo("AI_DISABLED");

        jdbc.update("update feature_switch set enabled = true, reason = null where name = 'ai_assistant'");
        jdbc.execute("alter table feature_switch rename to feature_switch_moved");
        try {
            clock.advance(Duration.ofSeconds(31));
            String other = createConversation(customer);
            assertThat(send(customer, other, "xin chào").body()).contains("\"handedOff\":true");
            assertThat(providerBodies()).as("unreadable switch = off").hasSize(before);
            assertThat(handoffReason(other, 0)).isEqualTo("AI_DISABLED");
        } finally {
            jdbc.execute("alter table feature_switch_moved rename to feature_switch");
        }
        // Recovers as soon as the state can be read again (an unreadable state is never cached).
        String third = createConversation(customer);
        assertThat(send(customer, third, "xin chào").body()).contains(DEFAULT_ANSWER);
    }

    /**
     * T-SEC06 with a turn in flight (architecture review M2): the model is answering when the operator
     * switches the AI off. The tool it proposed does not run and no second provider call is made; the call
     * already in flight is not cancelled (decision 6A).
     */
    @Test
    void aTurnInFlightWhenTheSwitchGoesOffRunsNoToolAndNoFurtherCall() throws Exception {
        DemoHttpClient customer = loggedIn("cust-a");
        String conversation = createConversation(customer);
        // Review round 2 (backend L2): the operator logs in before the turn starts, and the first answer is
        // delayed 5 s, so the switch is off well before it arrives even on a slow machine.
        DemoHttpClient ops = loggedIn("ops-1");
        scriptThenStatus(500, 5_000, toolCall("searchVerifiedProduct", "{\"oe\":\"04465-0K290\"}"),
                text("không được dùng"));
        int before = providerBodies().size();
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<HttpResponse<String>> turn =
                    pool.submit(() -> send(customer, conversation, "Má phanh 04465-0K290"));
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (providerBodies().size() == before && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(providerBodies()).as("first model call in flight").hasSize(before + 1);
            assertThat(ops.postJson(SWITCH, OFF).statusCode()).isEqualTo(200);

            HttpResponse<String> answer = turn.get();

            assertThat(answer.body()).contains("\"handedOff\":true").doesNotContain("không được dùng");
        }
        assertThat(providerBodies()).as("no second call").hasSize(before + 1);
        assertThat(handoffReason(conversation, 0)).isEqualTo("AI_DISABLED");
        assertThat(jdbc.queryForObject("select count(*) from tool_invocation", Integer.class)).isZero();
    }

    /**
     * T-SEC07 during a flow (architecture review M1): a quote was issued by the first steps of the turn, then
     * the provider rejects the key. The turn hands off (PROVIDER_AUTH), the card is not shown, no order is
     * written, and the issued quote stays as it was (ADR-013: no transition after the failure).
     */
    @Test
    void aKeyRevokedAfterAQuoteWasIssuedWritesNoOrderAndShowsNoCard() {
        DemoHttpClient customer = loggedIn("cust-a");
        String conversation = createConversation(customer);
        scriptThenStatus(401, 0, toolCall("searchVerifiedProduct", "{\"oe\":\"04465-0K290\"}"),
                quoteFirstCandidate(2));

        HttpResponse<String> answer = send(customer, conversation, "Báo giá 2 bộ má phanh 04465-0K290");

        assertThat(answer.body()).contains("\"handedOff\":true").doesNotContain("confirmationToken")
                .doesNotContain("555444333");
        assertThat(providerBodies()).hasSize(3);
        assertThat(handoffReason(conversation, 0)).isEqualTo("PROVIDER_AUTH");
        assertThat(jdbc.queryForList("select status from quote", String.class)).containsExactly("ISSUED");
        assertThat(jdbc.queryForObject("select count(*) from draft_order_request", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from quote_confirmation", Integer.class)).isZero();
    }

    /**
     * T-SEC07 (AC-08.2, decision 5A): the provider rejects the key. The turn hands off (PROVIDER_AUTH) with a
     * safe answer, the breaker opens, no later turn reaches the provider and nothing is written for an order;
     * only an operator closes the breaker.
     */
    @Test
    void aRevokedKeyOpensTheBreakerUntilAnOperatorClosesIt(CapturedOutput output) {
        DemoHttpClient customer = loggedIn("cust-a");
        String first = createConversation(customer);
        providerStatus(401);

        HttpResponse<String> answer = send(customer, first, "Tôi cần má phanh cho xe");

        assertThat(answer.statusCode()).isEqualTo(200);
        assertThat(ChatBodies.withoutTurnId(answer.body())).contains("\"handedOff\":true").doesNotContain("Exception")
                .doesNotContain("401")
                .doesNotContain("at com.");
        assertThat(handoffReason(first, 0)).isEqualTo("PROVIDER_AUTH");
        assertThat(switchEvents("PROVIDER_AUTH", false)).isEqualTo(1);
        int calls = providerBodies().size();
        assertThat(calls).as("401 is not retried").isEqualTo(1);

        script(); // the provider would answer again, but the breaker stays open
        String second = createConversation(customer);
        assertThat(send(customer, second, "xin chào").body()).contains("\"handedOff\":true");
        assertThat(providerBodies()).hasSize(calls);
        assertThat(handoffReason(second, 0)).isEqualTo("PROVIDER_AUTH");
        assertThat(jdbc.queryForObject("select count(*) from quote", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from draft_order_request", Integer.class)).isZero();

        DemoHttpClient ops = loggedIn("ops-1");
        assertThat(ops.get(SWITCH).body()).contains("\"aiEnabled\":false").contains("\"reason\":\"PROVIDER_AUTH\"");
        assertThat(ops.postJson(SWITCH, ON).body()).contains("\"aiEnabled\":true");

        String third = createConversation(customer);
        assertThat(send(customer, third, "xin chào").body()).contains(DEFAULT_ANSWER);
        assertThat(output.getAll()).doesNotContain("usr_").contains("platform.kill_switch changed");
    }
}
