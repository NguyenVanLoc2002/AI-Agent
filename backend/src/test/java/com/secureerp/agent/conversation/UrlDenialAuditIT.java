package com.secureerp.agent.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.DemoHttpClient;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * EP-09d over HTTP (A-45 3f, A-47): refusals that Spring Security answers at the URL level (401, 403 for a role,
 * 403 for CSRF) are ACCESS_DENIED events by area of the URL, collapsed per minute; the answers do not change, the
 * URL, its parameters and the body are never stored or logged.
 */
@ExtendWith(OutputCaptureExtension.class)
class UrlDenialAuditIT extends ChatEnvironment {

    @Autowired
    TransactionTemplate transactions;

    private List<String> denials() {
        return jdbc.queryForList("select resource_type || ':' || outcome || ':' || coalesce(actor_role, '-') || ':' "
                + "|| occurrences from audit_event where action = 'ACCESS_DENIED' order by id", String.class);
    }

    @Test
    void urlLevelRefusalsAreAuditedByAreaAndCollapsed(CapturedOutput output) {
        DemoHttpClient anonymous = client();
        anonymous.csrf();
        assertThat(anonymous.get("/api/internal/handoffs?probe=987654321").statusCode()).isEqualTo(401);
        for (int i = 0; i < 3; i++) {
            assertThat(anonymous.get("/api/demo/security/events").statusCode()).isEqualTo(401);
        }
        assertThat(anonymous.get("/api/nothing-here").statusCode()).isEqualTo(401);
        assertThat(anonymous.get("/api/%69nternal/handoffs").statusCode()).as("encoded letter").isEqualTo(401);
        assertThat(loggedIn("cust-a").get("/api/ops/kill-switch").statusCode()).isEqualTo(403);
        assertThat(loggedIn("sales-1").get("/api/demo/security/events").statusCode()).isEqualTo(403);
        DemoHttpClient sales = loggedIn("sales-1");
        sales.dropCsrf();
        assertThat(sales.postEmpty("/api/internal/handoffs/1/claim").statusCode()).as("no CSRF token").isEqualTo(403);

        assertThat(denials()).containsExactly(
                "ENDPOINT_INTERNAL:UNAUTHENTICATED:-:2",
                "ENDPOINT_SECURITY:UNAUTHENTICATED:-:3",
                "ENDPOINT_OTHER:UNAUTHENTICATED:-:1",
                "ENDPOINT_OPS:FORBIDDEN:CUST:1",
                "ENDPOINT_SECURITY:FORBIDDEN:SALES:1",
                "ENDPOINT_INTERNAL:CSRF_REJECTED:SALES:1");
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'ACCESS_DENIED' "
                + "and actor_role is null and actor is null", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'ACCESS_DENIED' "
                + "and actor_role is not null and actor like 'usr\\_%'", Integer.class)).isEqualTo(3);
        assertThat(output.getAll()).contains("url.denied code=UNAUTHENTICATED area=ENDPOINT_INTERNAL")
                .doesNotContain("987654321").doesNotContain("probe=").doesNotContain("separate_write_inside_transaction");
    }

    /** Decision 4A: the answer never depends on the audit: without the table the refusals are the same. */
    @Test
    void anAuditFailureDoesNotChangeTheAnswer() {
        DemoHttpClient customer = loggedIn("cust-a");
        DemoHttpClient anonymous = client();
        anonymous.csrf();
        jdbc.execute("alter table audit_event rename to audit_event_moved");
        HttpResponse<String> forbidden;
        HttpResponse<String> unauthenticated;
        try {
            forbidden = customer.get("/api/ops/kill-switch");
            unauthenticated = anonymous.get("/api/internal/handoffs");
        } finally {
            jdbc.execute("alter table audit_event_moved rename to audit_event");
        }
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(forbidden.body()).doesNotContain("audit_event");
        assertThat(unauthenticated.statusCode()).isEqualTo(401);
        assertThat(denials()).isEmpty();
    }

    /**
     * Review M-1: anonymous refusals cannot tie up the pool. With the audit table locked, at most two writers wait
     * for it; every other refusal is answered at once without its record; requests that need the database still
     * run; once the lock is gone the waiting ones are written.
     */
    @Test
    void aBlockedAuditTableDoesNotHoldRefusalsOrThePool() throws Exception {
        DemoHttpClient ops = loggedIn("ops-1");
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(12)) {
            Future<?> holder = pool.submit(() -> transactions.executeWithoutResult(tx -> {
                jdbc.execute("lock table audit_event in access exclusive mode");
                locked.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            List<Future<Integer>> refusals = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                refusals.add(pool.submit(() -> {
                    DemoHttpClient anonymous = client();
                    return anonymous.get("/api/internal/handoffs").statusCode();
                }));
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (refusals.stream().filter(Future::isDone).count() < 8 && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertThat(refusals.stream().filter(Future::isDone).count()).as("answered while the table is locked")
                    .isEqualTo(8);
            assertThat(ops.get("/api/ops/kill-switch").statusCode()).as("the pool is not exhausted").isEqualTo(200);

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            for (Future<Integer> refusal : refusals) {
                assertThat(refusal.get(10, TimeUnit.SECONDS)).isEqualTo(401);
            }
        }
        assertThat(denials()).containsExactly("ENDPOINT_INTERNAL:UNAUTHENTICATED:-:2");
    }

    /** A refusal of a service (not of the URL) is still recorded once, by the service, not twice. */
    @Test
    void aServiceRefusalIsNotAlsoAUrlRefusal() {
        DemoHttpClient ops = loggedIn("ops-1");
        assertThat(ops.get("/api/ops/kill-switch").statusCode()).isEqualTo(200);
        assertThat(loggedIn("cust-a").get("/api/demo/quotes/BG-ZZZZZZZZ").statusCode()).isEqualTo(404);

        assertThat(denials()).containsExactly("QUOTE:FORBIDDEN_RESOURCE:CUST:1");
    }
}
