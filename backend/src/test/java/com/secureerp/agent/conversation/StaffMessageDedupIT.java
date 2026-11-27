package com.secureerp.agent.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.DemoHttpClient;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * STAFF-MSG-ID (A-91, owner 2026-11-27 1A–4A) over HTTP with PostgreSQL: a staff message carries a required
 * {@code clientEventId}; a resend with the same ID and text is stored once and audited once (204 both times), the
 * ID of another message is 409, and the event ID never opens a conversation the caller does not hold.
 *
 * <p>Demo configuration: cust-a is served by team-1 (sales-1), cust-b by team-2 (sales-2).
 */
@ExtendWith(OutputCaptureExtension.class)
class StaffMessageDedupIT extends ChatEnvironment {

    private static final Pattern HANDOFF_ID = Pattern.compile("\"handoffId\":(\\d+)");

    private record Held(DemoHttpClient customer, DemoHttpClient sales, String conversation, String handoffId) {

        String messages() {
            return "/api/internal/conversations/" + conversation + "/messages";
        }
    }

    /** A conversation of cust-a handed to staff and claimed by sales-1. */
    private Held held() {
        DemoHttpClient customer = loggedIn("cust-a");
        String conversation = createConversation(customer);
        providerStatus(500);
        assertThat(send(customer, conversation, "Tôi cần tư vấn má phanh").body()).contains("\"handedOff\":true");
        script();
        DemoHttpClient sales = loggedIn("sales-1");
        Matcher matcher = HANDOFF_ID.matcher(sales.get("/api/internal/handoffs").body());
        assertThat(matcher.find()).isTrue();
        String handoffId = matcher.group(1);
        assertThat(sales.postEmpty("/api/internal/handoffs/" + handoffId + "/claim").statusCode()).isEqualTo(200);
        return new Held(customer, sales, conversation, handoffId);
    }

    private static String body(UUID clientEventId, String text) {
        return "{\"clientEventId\":\"" + clientEventId + "\",\"text\":\"" + text + "\"}";
    }

    private int staffMessages(String conversation) {
        return jdbc.queryForObject("select count(*) from message where conversation_id = ? and direction = 'STAFF'",
                Integer.class, conversation);
    }

    private int postedAudits() {
        return jdbc.queryForObject("select count(*) from audit_event where action = 'STAFF_MESSAGE_POSTED'",
                Integer.class);
    }

    @Test
    void theSameEventAgainIsStoredAndAuditedOnce(CapturedOutput output) {
        Held held = held();
        UUID event = UUID.randomUUID();
        String text = "Chào anh, em là nhân viên STAFFDEDUP42";

        assertThat(held.sales().postJson(held.messages(), body(event, text)).statusCode()).isEqualTo(204);
        HttpResponse<String> again = held.sales().postJson(held.messages(), body(event, text));

        assertThat(again.statusCode()).isEqualTo(204);
        assertThat(again.body()).isEmpty();
        assertThat(staffMessages(held.conversation())).isEqualTo(1);
        assertThat(postedAudits()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select client_event_id from message where direction = 'STAFF'", UUID.class))
                .isEqualTo(event);
        assertThat(held.customer().get("/api/demo/conversations/" + held.conversation() + "/messages").body())
                .containsOnlyOnce("STAFFDEDUP42");
        // A new event is a new message.
        assertThat(held.sales().postJson(held.messages(), body(UUID.randomUUID(), text)).statusCode()).isEqualTo(204);
        assertThat(staffMessages(held.conversation())).isEqualTo(2);
        assertThat(postedAudits()).isEqualTo(2);
        assertThat(output.getAll()).doesNotContain("STAFFDEDUP42").contains("conversation.staff_message duplicate");
    }

    @Test
    void parallelSendsOfOneEventStoreOneMessage() throws Exception {
        Held held = held();
        UUID event = UUID.randomUUID();
        int senders = 10;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> answers = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(senders)) {
            for (int i = 0; i < senders; i++) {
                answers.add(pool.submit(() -> {
                    start.await();
                    return held.sales().postJson(held.messages(), body(event, "Một tin duy nhất"));
                }));
            }
            start.countDown();
            for (Future<HttpResponse<String>> answer : answers) {
                // 204 (stored or duplicate) or 409 when the handoff lock was busy; never a second message.
                assertThat(answer.get().statusCode()).isIn(204, 409);
            }
        }
        assertThat(answers.stream().filter(a -> a.resultNow().statusCode() == 204)).isNotEmpty();
        assertThat(staffMessages(held.conversation())).isEqualTo(1);
        assertThat(postedAudits()).isEqualTo(1);
    }

    @Test
    void theIdOfAnotherMessageIsAConflictAndWritesNothing(CapturedOutput output) {
        Held held = held();
        UUID event = UUID.randomUUID();
        assertThat(held.sales().postJson(held.messages(), body(event, "Tin thứ nhất")).statusCode()).isEqualTo(204);

        HttpResponse<String> otherText = held.sales().postJson(held.messages(), body(event, "Tin khác STAFFREUSE9"));
        assertThat(otherText.statusCode()).isEqualTo(409);
        assertThat(otherText.body()).contains("\"code\":\"CONFLICT\"").doesNotContain("STAFFREUSE9");

        // The event ID of the customer's own message in the same conversation.
        UUID customerEvent = jdbc.queryForObject("select client_event_id from message where conversation_id = ? "
                + "and direction = 'CUSTOMER' order by message_id limit 1", UUID.class, held.conversation());
        assertThat(held.sales().postJson(held.messages(), body(customerEvent, "Tôi cần tư vấn má phanh"))
                .statusCode()).isEqualTo(409);

        assertThat(staffMessages(held.conversation())).isEqualTo(1);
        assertThat(postedAudits()).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain("STAFFREUSE9").contains("reason=EVENT_ID_REUSED");
    }

    @Test
    void aMissingOrMalformedEventIdIsRefused() {
        Held held = held();
        for (String invalid : List.of("{\"text\":\"Không có mã\"}",
                "{\"clientEventId\":null,\"text\":\"Mã rỗng\"}",
                "{\"clientEventId\":\"not-a-uuid\",\"text\":\"Mã sai\"}",
                "{\"clientEventId\":\"" + UUID.randomUUID() + "\",\"text\":\"   \"}")) {
            assertThat(held.sales().postJson(held.messages(), invalid).statusCode()).as(invalid).isEqualTo(400);
        }
        assertThat(staffMessages(held.conversation())).isZero();
        assertThat(postedAudits()).isZero();
    }

    /** The event ID is not a key to the conversation: only the holder writes, before and after a resolve. */
    @Test
    void anEventIdNeverOpensAConversationTheCallerDoesNotHold() {
        Held held = held();
        UUID event = UUID.randomUUID();
        String text = "Tin của người đang giữ";
        assertThat(held.sales().postJson(held.messages(), body(event, text)).statusCode()).isEqualTo(204);

        DemoHttpClient otherTeam = loggedIn("sales-2");
        assertThat(otherTeam.postJson(held.messages(), body(event, text)).statusCode()).isEqualTo(404);
        assertThat(otherTeam.postJson(held.messages(), body(UUID.randomUUID(), "x")).statusCode()).isEqualTo(404);
        assertThat(loggedIn("cust-a").postJson(held.messages(), body(event, text)).statusCode()).isEqualTo(403);

        assertThat(held.sales().postEmpty("/api/internal/handoffs/" + held.handoffId() + "/resolve").statusCode())
                .isEqualTo(200);
        // After the resolve even the former holder's resend of a stored event is the same 404 (A-15).
        assertThat(held.sales().postJson(held.messages(), body(event, text)).statusCode()).isEqualTo(404);
        assertThat(staffMessages(held.conversation())).isEqualTo(1);
        assertThat(postedAudits()).isEqualTo(1);
    }

    /** Review L-1: a customer event that reuses a staff event ID is no turn of the customer and stores nothing. */
    @Test
    void aCustomerEventWithAStaffEventIdStoresNothing() {
        Held held = held();
        UUID event = UUID.randomUUID();
        assertThat(held.sales().postJson(held.messages(), body(event, "Tin của nhân viên")).statusCode()).isEqualTo(204);
        String staffTurn = jdbc.queryForObject("select turn_id::text from message where direction = 'STAFF'", String.class);
        // Older than the turn timeout: a duplicate customer turn without a reply would then be handed off again.
        jdbc.update("update message set created_at = created_at - interval '1 day' where direction = 'STAFF'");
        int messages = jdbc.queryForObject("select count(*) from message", Integer.class);
        int handoffs = jdbc.queryForObject("select count(*) from handoff", Integer.class);

        HttpResponse<String> reused = held.customer().postJson("/api/demo/conversations/" + held.conversation()
                + "/messages", body(event, "Tin của nhân viên"));

        assertThat(reused.statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from message", Integer.class)).isEqualTo(messages);
        assertThat(jdbc.queryForObject("select count(*) from message where turn_id::text = ? and direction = 'BOT'",
                Integer.class, staffTurn)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from handoff", Integer.class)).isEqualTo(handoffs);
    }

    /** Review L-2: V20 requires the event ID of every staff row written from now on, whatever the write path. */
    @Test
    void theDatabaseRefusesANewStaffMessageWithoutAnEventId() {
        Held held = held();
        assertThatThrownBy(() -> jdbc.update("insert into message (conversation_id, turn_id, direction, text, created_at) "
                + "values (?, gen_random_uuid(), 'STAFF', 'x', now())", held.conversation()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("message_staff_client_event");
    }
}
