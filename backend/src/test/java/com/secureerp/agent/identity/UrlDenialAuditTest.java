package com.secureerp.agent.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.secureerp.agent.audit.AuditEvent;
import com.secureerp.agent.audit.AuditTrail;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

/** EP-09d (A-47 2A): the area of a refused URL, by path prefix only; a look-alike prefix is another area. */
class UrlDenialAuditTest {

    private static AuditEvent.Resource area(String context, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setContextPath(context);
        request.setServletPath("");
        return UrlDenialAudit.area(request);
    }

    @Test
    void everyPathMapsToItsArea() {
        Map<String, AuditEvent.Resource> expected = Map.ofEntries(
                Map.entry("/api/internal/handoffs", AuditEvent.Resource.ENDPOINT_INTERNAL),
                Map.entry("/api/internal", AuditEvent.Resource.ENDPOINT_INTERNAL),
                Map.entry("/api/internal;jsessionid=x", AuditEvent.Resource.ENDPOINT_INTERNAL),
                Map.entry("/api/ops/kill-switch", AuditEvent.Resource.ENDPOINT_OPS),
                Map.entry("/api/demo/security/events", AuditEvent.Resource.ENDPOINT_SECURITY),
                Map.entry("/api/demo/security", AuditEvent.Resource.ENDPOINT_SECURITY),
                Map.entry("/api/demo/quotes/BG-ZZZZZZZZ", AuditEvent.Resource.ENDPOINT_DEMO),
                Map.entry("/api/demo/securityx", AuditEvent.Resource.ENDPOINT_DEMO),
                Map.entry("/api/internalx/handoffs", AuditEvent.Resource.ENDPOINT_OTHER),
                Map.entry("/api/opsy", AuditEvent.Resource.ENDPOINT_OTHER),
                Map.entry("/", AuditEvent.Resource.ENDPOINT_OTHER),
                Map.entry("/API/INTERNAL/handoffs", AuditEvent.Resource.ENDPOINT_OTHER),
                // Review L-1: decoded as the authorization rules see it.
                Map.entry("/api/%6fps/kill-switch", AuditEvent.Resource.ENDPOINT_OPS),
                Map.entry("/api/%69nternal/handoffs", AuditEvent.Resource.ENDPOINT_INTERNAL));
        expected.forEach((uri, area) -> assertThat(area("", uri)).as(uri).isEqualTo(area));
    }

    /** Review L-4: an audit that throws never turns a refusal into an error; it is also skipped when busy (M-1). */
    @Test
    void aFailingOrBusyAuditNeverThrows() {
        AuditTrail failing = new AuditTrail() {
            @Override
            public void record(AuditEvent event) {
                throw new IllegalStateException("down");
            }

            @Override
            public void recordSeparately(AuditEvent event) {
                throw new IllegalStateException("down");
            }
        };
        UrlDenialAudit audit = new UrlDenialAudit(failing, null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/internal/handoffs");

        assertThatCode(() -> audit.unauthenticated(request)).doesNotThrowAnyException();
        assertThatCode(() -> audit.forbidden(request, new AccessDeniedException("no"))).doesNotThrowAnyException();
    }

    @Test
    void theContextPathIsNotPartOfTheArea() {
        assertThat(area("/app", "/app/api/ops/kill-switch")).isEqualTo(AuditEvent.Resource.ENDPOINT_OPS);
    }
}
