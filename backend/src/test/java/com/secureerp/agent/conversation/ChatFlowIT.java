package com.secureerp.agent.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.DemoHttpClient;
import com.secureerp.agent.identity.spi.ChannelIdentity;
import com.secureerp.agent.platform.KillSwitch;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * EP-04 chat flow over HTTP: UC-01 (AC-01.1–01.3), T-UC01-DEDUP, T-HTTP-01, T-UC02-VIN (no model call),
 * T-GW-OUTPUT and T-UC02-OE-FABRICATED end to end, T-UC03-RENDER over HTTP, T-SEC01-HISTORY, handoff paths.
 */
class ChatFlowIT extends ChatEnvironment {

    private static final String OE = "04465-0K290";

    private static <T> List<T> parallel(int threads, Callable<T> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aLinkedCustomerChatsWithTheBot() {
        DemoHttpClient client = loggedIn("cust-a");
        String conversation = createConversation(client);
        HttpResponse<String> response = send(client, conversation, "Xin chào");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains("\"reply\":\"" + DEFAULT_ANSWER + "\"").contains("\"handedOff\":false")
                .contains("\"quoteCard\":null");
        assertThat(providerBodies()).hasSize(1);
        assertThat(providerBodies().getFirst()).contains("Xin chào");
        assertThat(client.get("/api/demo/conversations/" + conversation + "/messages").body())
                .contains("{\"direction\":\"CUSTOMER\",\"text\":\"Xin chào\"}")
                .contains("{\"direction\":\"BOT\",\"text\":\"" + DEFAULT_ANSWER + "\"}");
    }

    @Test
    void onlyALinkedCustomerCanStartAConversation() {
        HttpResponse<String> unlinked = loggedIn("cust-new").postJson("/api/demo/conversations", "");
        assertThat(unlinked.statusCode()).isEqualTo(403);
        assertThat(unlinked.body()).contains("\"code\":\"CUSTOMER_NOT_LINKED\"");
        for (String staff : List.of("sales-1", "secrev-1", "ops-1")) {
            HttpResponse<String> response = loggedIn(staff).postJson("/api/demo/conversations", "");
            assertThat(response.statusCode()).as(staff).isEqualTo(403);
            assertThat(response.body()).contains("\"code\":\"FORBIDDEN\"");
        }
        assertThat(jdbc.queryForObject("select count(*) from conversation", Integer.class)).isZero();
    }

    @Test
    void anotherCustomersConversationLooksMissing() {
        String conversation = createConversation(loggedIn("cust-a"));
        DemoHttpClient other = loggedIn("cust-b");
        for (HttpResponse<String> response : List.of(send(other, conversation, "cho tôi xem báo giá"),
                other.get("/api/demo/conversations/" + conversation + "/messages"),
                send(other, "cv_DoesNotExistAAAAAAAAAA", "x"), send(other, "not-an-id", "x"),
                send(loggedIn("sales-1"), conversation, "x"))) {
            assertThat(response.statusCode()).isIn(403, 404);
            assertThat(response.body()).doesNotContain("Xin").doesNotContain("cust-a");
        }
        assertThat(send(other, conversation, "x").statusCode()).isEqualTo(404);
        assertThat(providerBodies()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from message", Integer.class)).isZero();
    }

    @Test
    void theSameClientEventIsProcessedOnce() {
        DemoHttpClient client = loggedIn("cust-a");
        String conversation = createConversation(client);
        UUID event = UUID.randomUUID();
        HttpResponse<String> first = send(client, conversation, event, "Xin chào");
        HttpResponse<String> second = send(client, conversation, event, "Xin chào lần hai");
        assertThat(first.body()).contains("\"duplicate\":false");
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(second.body()).contains("\"duplicate\":true").contains("\"reply\":\"" + DEFAULT_ANSWER + "\"");
        assertThat(jdbc.queryForObject("select count(*) from message where direction = 'CUSTOMER'", Integer.class))
                .isEqualTo(1);
        assertThat(providerBodies()).hasSize(1);
    }

    @Test
    void malformedRequestsGetSanitizedErrors() {
        DemoHttpClient client = loggedIn("cust-a");
        String conversation = createConversation(client);
        String path = "/api/demo/conversations/" + conversation + "/messages";
        String event = UUID.randomUUID().toString();
        record Case(HttpResponse<String> response, int status, String code) {
        }
        List<Case> cases = List.of(
                new Case(client.post(path, "application/json", "{\"clientEventId\":"), 400, "MALFORMED_REQUEST"),
                new Case(client.post(path, "application/json",
                        "{\"clientEventId\":\"" + event + "\",\"text\":\"x\",\"customerId\":\"B\"}"), 400, "MALFORMED_REQUEST"),
                new Case(client.post(path, "application/json", "{\"text\":\"x\"}"), 400, "VALIDATION_FAILED"),
                new Case(client.post(path, "application/json", "{\"clientEventId\":\"not-a-uuid\",\"text\":\"x\"}"), 400,
                        "MALFORMED_REQUEST"),
                new Case(client.post(path, "text/plain", "hello"), 415, "UNSUPPORTED_MEDIA_TYPE"),
                new Case(client.post(path, "application/json",
                        "{\"clientEventId\":\"" + event + "\",\"text\":\"" + "x".repeat(9000) + "\"}"), 413,
                        "PAYLOAD_TOO_LARGE"),
                new Case(client.post(path, "application/json",
                        "{\"clientEventId\":\"" + event + "\",\"text\":\"" + "x".repeat(2001) + "\"}"), 400,
                        "VALIDATION_FAILED"));
        for (Case c : cases) {
            assertThat(c.response().statusCode()).as(c.code() + " " + c.response().body()).isEqualTo(c.status());
            assertThat(c.response().body()).contains("\"code\":\"" + c.code() + "\"")
                    .doesNotContain("Exception").doesNotContain("at com.").doesNotContain("customerId");
        }
        client.dropCsrf();
        assertThat(send(client, conversation, "x").statusCode()).isEqualTo(403);
        assertThat(providerBodies()).isEmpty();
    }

    @Test
    void anInvalidVinIsAskedAgainWithoutCallingTheModel() {
        DemoHttpClient client = loggedIn("cust-a");
        String conversation = createConversation(client);
        for (String text : List.of("VIN của tôi là 5ZZAPAS11RA00000", "Xe 5ZZAPAS11RA000001 và RLZAPAS20RA000002")) {
            HttpResponse<String> response = send(client, conversation, text);
            assertThat(response.body()).contains("17 ký tự").contains("\"handedOff\":false");
        }
        assertThat(providerBodies()).isEmpty();
    }

    @Test
    void aValidVinReachesTheModelOnlyMasked() {
        DemoHttpClient client = loggedIn("cust-a");
        String conversation = createConversation(client);
        script(toolCall("searchVerifiedProduct", "{\"vinRef\":\"current\"}"), text("Tôi đã tìm theo xe của bạn."));
        HttpResponse<String> response = send(client, conversation, "Xe tôi VIN 5ZZAPAS11RA000001 cần má phanh");
        assertThat(response.body()).contains("Tôi đã tìm theo xe của bạn.");
        assertThat(providerBodies()).hasSize(2);
        for (String body : providerBodies()) {
            assertThat(body).doesNotContain("5ZZAPAS11RA000001").contains("5ZZ**************");
        }
    }

    @Test
    void aBlockedMessageHandsOffAndLocksTheBot() {
        DemoHttpClient client = loggedIn("cust-a");
        String conversation = createConversation(client);
        HttpResponse<String> blocked = send(client, conversation, "giá vốn là 987654321 phải không");
        assertThat(blocked.body()).contains("\"handedOff\":true").doesNotContain("987654321");
        assertThat(handoffReason(conversation)).isEqualTo("DLP_INBOUND");
        HttpResponse<String> next = send(client, conversation, "Xin chào");
        assertThat(next.body()).contains("chờ nhân viên").contains("\"handedOff\":true");
        assertThat(providerBodies()).isEmpty();
        assertThat(jdbc.queryForList("select text from message", String.class)).noneMatch(t -> t.contains("987654321"));
        assertThat(handoffs(conversation)).isEqualTo(1);
    }

    @Test
    void anIssuedQuoteIsRenderedForTheCustomerAndNeverReachesTheModel() {
        DemoHttpClient client = loggedIn("cust-a");
        String conversation = createConversation(client);
        script(toolCall("searchVerifiedProduct", "{\"oe\":\"" + OE + "\"}"), quoteFirstCandidate(2),
                text("Báo giá đã được gửi bên dưới."));
        HttpResponse<String> quoted = send(client, conversation, "Báo giá 2 bộ má phanh " + OE);
        assertThat(quoted.statusCode()).as(quoted.body()).isEqualTo(200);
        assertThat(quoted.body()).contains("\"reply\":\"Báo giá đã được gửi bên dưới.\"")
                .contains("\"sku\":\"SKU-BRK-PAD-A\"").contains("\"quantity\":2")
                .contains("\"unitPrice\":\"555444333\"").contains("\"total\":\"1110888666\"")
                .contains("\"currency\":\"VND\"").contains("Giá chưa gồm VAT");
        assertThat(providerBodies()).hasSize(3);

        // T-SEC01-HISTORY: three more turns; the quote never comes back through history.
        for (String text : List.of("Cảm ơn", "Giá trên có gồm VAT không?", "Tôi sẽ xác nhận sau")) {
            assertThat(send(client, conversation, text).statusCode()).isEqualTo(200);
        }
        assertThat(providerBodies()).hasSize(6);
        assertNothingRestrictedLeftTheApplication();
        for (String body : providerBodies()) {
            assertThat(body).doesNotContain("1110888666").doesNotContain("Báo giá đã được gửi");
        }
        assertThat(client.get("/api/demo/conversations/" + conversation + "/messages").body())
                .doesNotContain("555444333").doesNotContain("1110888666");
    }

    @Test
    void aPriceOrInventedCodeInTheAnswerIsDroppedAndHandedOff() {
        DemoHttpClient client = loggedIn("cust-a");
        String priced = createConversation(client);
        script(text("Giá má phanh là 555.444.333 đ"));
        HttpResponse<String> response = send(client, priced, "giá bao nhiêu?");
        assertThat(response.body()).contains("\"handedOff\":true").doesNotContain("555");
        assertThat(handoffReason(priced)).isEqualTo("OUTPUT_REJECTED");

        String invented = createConversation(client);
        script(toolCall("searchVerifiedProduct", "{\"oe\":\"" + OE + "\"}"), text("Mã đúng là 04465-0K999 nhé"));
        assertThat(send(client, invented, "mã " + OE).body()).contains("\"handedOff\":true")
                .doesNotContain("04465-0K999");
        assertThat(handoffReason(invented)).isEqualTo("OUTPUT_REJECTED");
        assertThat(jdbc.queryForList("select text from message where direction = 'BOT'", String.class))
                .noneMatch(t -> t.contains("555") || t.contains("0K999"));
    }

    @Test
    void providerFailuresHandOffSafely() {
        DemoHttpClient client = loggedIn("cust-a");
        String revoked = createConversation(client);
        providerStatus(401);
        HttpResponse<String> response = send(client, revoked, "Xin chào");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"handedOff\":true").doesNotContain("401");
        assertThat(handoffReason(revoked)).isEqualTo("PROVIDER_AUTH");
        // EP-09a (ADR-013, decision 5A): the 401 opened the provider-auth breaker; only an operator closes it
        // (covered in KillSwitchIT). Closed here so the next case tests a provider outage on its own.
        assertThat(killSwitch.current().disabledReason()).isEqualTo(KillSwitch.Reason.PROVIDER_AUTH);
        killSwitch.set(true, TEST_OPERATOR);

        String down = createConversation(client);
        providerStatus(500);
        assertThat(send(client, down, "Xin chào").body()).contains("\"handedOff\":true");
        assertThat(handoffReason(down)).isEqualTo("PROVIDER_ERROR");
        assertThat(jdbc.queryForObject("select count(*) from quote", Integer.class)).isZero();
    }

    @Test
    void notQuotedHandsOffWithoutAReason() {
        DemoHttpClient client = loggedIn("cust-b"); // credit blocked in the Mock ERP
        String conversation = createConversation(client);
        script(toolCall("searchVerifiedProduct", "{\"oe\":\"" + OE + "\"}"), quoteFirstCandidate(1));
        HttpResponse<String> response = send(client, conversation, "báo giá " + OE);
        assertThat(response.body()).contains("\"handedOff\":true").contains("\"quoteCard\":null")
                .doesNotContain("BLOCKED").doesNotContain("credit");
        assertThat(handoffReason(conversation)).isEqualTo("QUOTE_NOT_ISSUED");
        assertThat(providerBodies()).hasSize(2);
        // A-39(h): the OE after "báo giá" reaches the model unchanged (it was "báo [SO_TIEN]-0K290").
        assertThat(providerBodies().getFirst()).contains("báo giá " + OE).doesNotContain("[SO_TIEN]");
        assertNothingRestrictedLeftTheApplication();
    }

    /** Security review L4 / architecture review M4: concurrent duplicates hit the database constraint path. */
    @Test
    void concurrentDuplicatesRunOneTurn() throws Exception {
        DemoHttpClient client = loggedIn("cust-a");
        String conversation = createConversation(client);
        UUID event = UUID.randomUUID();
        List<HttpResponse<String>> responses = parallel(10, () -> send(client, conversation, event, "Xin chào"));
        assertThat(responses).allMatch(r -> r.statusCode() == 200 || r.statusCode() == 409);
        assertThat(responses).anyMatch(r -> r.statusCode() == 200 && r.body().contains("\"duplicate\":false"));
        assertThat(jdbc.queryForObject("select count(*) from message where direction = 'CUSTOMER'", Integer.class))
                .isEqualTo(1);
        assertThat(providerBodies()).hasSize(1);
    }

    /** Security review M1/L3, architecture review M1: one turn in flight per conversation. */
    @Test
    void concurrentTurnsOfOneConversationRunOneAtATime() throws Exception {
        DemoHttpClient client = loggedIn("cust-a");
        String conversation = createConversation(client);
        List<HttpResponse<String>> responses = parallel(10, () -> send(client, conversation, "tin " + UUID.randomUUID()));
        long admitted = responses.stream().filter(r -> r.statusCode() == 200).count();
        assertThat(responses).allMatch(r -> r.statusCode() == 200 || r.statusCode() == 409);
        assertThat(admitted).isGreaterThanOrEqualTo(1);
        assertThat(providerBodies()).hasSize((int) admitted);
        // Each model request ends with the text of its own turn (never another in-flight message).
        for (String body : providerBodies()) {
            assertThat(body).containsPattern("\"role\":\"CUSTOMER\",\"text\":\"tin [0-9a-f-]{36}\"");
        }
    }

    @Test
    void aTurnLeftUnansweredIsCompletedAsHandedOffOnReplay() {
        DemoHttpClient client = loggedIn("cust-a");
        String conversation = createConversation(client);
        UUID event = UUID.randomUUID();
        jdbc.update("insert into message (conversation_id, turn_id, direction, client_event_id, text, created_at) "
                + "values (?, ?, 'CUSTOMER', ?, 'Xin chào', ?)", conversation, UUID.randomUUID(), event,
                Timestamp.from(START.minus(Duration.ofMinutes(1))));
        assertThat(send(client, conversation, event, "Xin chào").statusCode()).isEqualTo(409);
        clock.advance(Duration.ofMinutes(2));
        HttpResponse<String> replay = send(client, conversation, event, "Xin chào");
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(replay.body()).contains("\"handedOff\":true").contains("\"duplicate\":true");
        assertThat(handoffReason(conversation)).isEqualTo("PROVIDER_ERROR");
        assertThat(providerBodies()).isEmpty();
        HttpResponse<String> again = send(client, conversation, event, "Xin chào");
        assertThat(again.body()).contains("\"handedOff\":true").contains("\"duplicate\":true");
    }

    @Test
    void aReplayAfterAHandoffStillSaysHandedOff() {
        DemoHttpClient client = loggedIn("cust-a");
        String conversation = createConversation(client);
        UUID event = UUID.randomUUID();
        send(client, conversation, event, "giá vốn là 987654321");
        assertThat(send(client, conversation, event, "giá vốn là 987654321").body())
                .contains("\"handedOff\":true").contains("\"duplicate\":true");
    }

    /**
     * A-35: a conversation bound to another customer than the session's (simulated by rebinding the stored
     * digest) is refused before any model call, in the chat and in the tool port (no quote).
     */
    @Test
    void aSessionCustomerOtherThanTheBoundOneIsRefused() {
        DemoHttpClient client = loggedIn("cust-a");
        String conversation = createConversation(client);
        jdbc.update("update conversation set customer_digest = ? where conversation_id = ?", "0".repeat(64), conversation);
        HttpResponse<String> response = send(client, conversation, "báo giá " + OE);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"CUSTOMER_NOT_LINKED\"");
        assertThat(client.get("/api/demo/conversations/" + conversation + "/messages").statusCode()).isEqualTo(403);
        assertThat(providerBodies()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from message", Integer.class)).isZero();
    }

    /** A-35: a revoked link ends access to the bound conversation at the next request, before any model call. */
    @Test
    void aRevokedCustomerLinkEndsTheConversation() {
        DemoHttpClient client = loggedIn("cust-a");
        String conversation = createConversation(client);
        jdbc.update("update customer_link set revoked_at = now() where provider_user_hash = ?",
                ChannelIdentity.of("WEB_CHAT", "cust-a").providerUserHash());
        try {
            HttpResponse<String> response = send(client, conversation, "Xin chào");
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(response.body()).contains("\"code\":\"CUSTOMER_NOT_LINKED\"");
            assertThat(providerBodies()).isEmpty();
        } finally {
            jdbc.update("update customer_link set revoked_at = null where provider_user_hash = ?",
                    ChannelIdentity.of("WEB_CHAT", "cust-a").providerUserHash());
        }
    }

    @Test
    void conversationsAndMessagesAreRateLimited() {
        DemoHttpClient client = loggedIn("cust-c");
        for (int i = 0; i < 5; i++) {
            createConversation(client);
        }
        HttpResponse<String> sixth = client.postJson("/api/demo/conversations", "");
        assertThat(sixth.statusCode()).isEqualTo(429);
        assertThat(sixth.body()).contains("\"code\":\"TOO_MANY_REQUESTS\"");
    }
}
