package com.secureerp.agent.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.DemoHttpClient;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * AUDIT-READS (A-45(j) residual; owner 2026-11-23, 1A–4A, DECISIONS_REQUIRED A-52; Flyway V18) over HTTP:
 * every staff read of a held conversation's messages is STAFF_CONVERSATION_READ, every read of a work list
 * (inbox, unknown draft orders, draft orders awaiting a decision) is STAFF_LIST_READ with the list as outcome.
 * Kind of resource only: no conversation ID, no text. Best effort (2A): the read is answered when the record
 * fails. Repeated reads of a minute are one row with a count (AUDIT-FLOOD).
 */
@ExtendWith(OutputCaptureExtension.class)
class StaffReadAuditIT extends ChatEnvironment {

    private static final Pattern HANDOFF_ID = Pattern.compile("\"handoffId\":(\\d+)");
    private static final String MESSAGE = "Tôi cần tư vấn má phanh";

    /** A conversation of cust-a handed to staff, claimed by sales-1; returns the conversation ID. */
    private String heldBy(DemoHttpClient sales) {
        DemoHttpClient customer = loggedIn("cust-a");
        String conversation = createConversation(customer);
        providerStatus(500);
        assertThat(send(customer, conversation, MESSAGE).body()).contains("\"handedOff\":true");
        script();
        Matcher id = HANDOFF_ID.matcher(sales.get("/api/internal/handoffs").body());
        assertThat(id.find()).isTrue();
        assertThat(sales.postEmpty("/api/internal/handoffs/" + id.group(1) + "/claim").statusCode()).isEqualTo(200);
        return conversation;
    }

    private List<Map<String, Object>> rows(String action) {
        return jdbc.queryForList("select * from audit_event where action = ? order by id", action);
    }

    @Test
    void everyStaffReadIsAuditedByKindAndCollapsedWithinAMinute() {
        DemoHttpClient sales = loggedIn("sales-1");
        String conversation = heldBy(sales);
        String messages = "/api/internal/conversations/" + conversation + "/messages";

        for (int i = 0; i < 3; i++) {
            HttpResponse<String> read = sales.get(messages);
            assertThat(read.statusCode()).isEqualTo(200);
            assertThat(read.body()).contains(MESSAGE);
        }
        sales.get("/api/internal/handoffs");
        assertThat(sales.get("/api/internal/orders/unknown").statusCode()).isEqualTo(200);
        assertThat(sales.get("/api/internal/orders/awaiting-decision").statusCode()).isEqualTo(200);

        assertThat(rows("STAFF_CONVERSATION_READ")).singleElement().satisfies(row -> {
            assertThat(row.get("resource_type")).isEqualTo("CONVERSATION");
            assertThat(row.get("outcome")).isEqualTo("READ");
            assertThat(row.get("actor_role")).isEqualTo("SALES");
            assertThat(row.get("actor")).asString().startsWith("usr_");
            assertThat(row.get("occurrences")).isEqualTo(3);
        });
        assertThat(rows("STAFF_LIST_READ")).extracting(row -> row.get("resource_type") + ":" + row.get("outcome")
                        + ":" + row.get("occurrences"))
                .containsExactlyInAnyOrder("HANDOFF:INBOX:2", "DRAFT_ORDER:UNKNOWN:1", "DRAFT_ORDER:AWAITING:1");
        // Never which conversation, never what was read.
        assertThat(jdbc.queryForList("select * from audit_event").toString()).doesNotContain(conversation)
                .doesNotContain("má phanh").doesNotContain("cust-a");
    }

    /** The customer reading their own conversation is not a staff read; a refused staff read is a denial only. */
    @Test
    void onlyAnAnsweredStaffReadIsRecorded() {
        DemoHttpClient sales = loggedIn("sales-1");
        String conversation = heldBy(sales);
        String messages = "/api/internal/conversations/" + conversation + "/messages";
        assertThat(loggedIn("cust-a").get("/api/demo/conversations/" + conversation + "/messages").statusCode())
                .isEqualTo(200);
        assertThat(loggedIn("sales-2").get(messages).statusCode()).isEqualTo(404);
        assertThat(loggedIn("cust-a").get("/api/internal/handoffs").statusCode()).isEqualTo(403);

        assertThat(rows("STAFF_CONVERSATION_READ")).isEmpty();
        assertThat(rows("STAFF_LIST_READ")).extracting(row -> row.get("outcome")).containsExactly("INBOX");
        assertThat(rows("ACCESS_DENIED")).isNotEmpty();
    }

    /** 2A: best effort. Without the audit table the staff member still gets the messages and the lists. */
    @Test
    void aReadIsAnsweredWhenItsRecordFails(CapturedOutput output) {
        DemoHttpClient sales = loggedIn("sales-1");
        String conversation = heldBy(sales);
        jdbc.execute("alter table audit_event rename to audit_event_moved");
        try {
            HttpResponse<String> read = sales.get("/api/internal/conversations/" + conversation + "/messages");
            assertThat(read.statusCode()).isEqualTo(200);
            assertThat(read.body()).contains(MESSAGE);
            assertThat(sales.get("/api/internal/handoffs").statusCode()).isEqualTo(200);
            assertThat(sales.get("/api/internal/orders/unknown").statusCode()).isEqualTo(200);
            assertThat(sales.get("/api/internal/orders/awaiting-decision").statusCode()).isEqualTo(200);
        } finally {
            jdbc.execute("alter table audit_event_moved rename to audit_event");
        }
        assertThat(rows("STAFF_CONVERSATION_READ")).isEmpty();
        assertThat(output.getAll()).contains("audit.event not_recorded exception=").doesNotContain(MESSAGE)
                .doesNotContain(conversation);
    }
}
