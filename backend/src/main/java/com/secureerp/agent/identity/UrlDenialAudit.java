package com.secureerp.agent.identity;

import com.secureerp.agent.audit.AuditActor;
import com.secureerp.agent.audit.AuditEvent;
import com.secureerp.agent.audit.AuditTrail;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.util.UrlPathHelper;

/**
 * EP-09d (A-45 3f, A-47): audits the refusals Spring Security answers at the URL level, before any service runs:
 * 401 (no session), 403 (role not allowed for the URL) and 403 for a missing or wrong CSRF token. Recorded as
 * ACCESS_DENIED with the area of the URL as resource type, never the URL, its parameters or the body (decisions
 * 1A–3A). Written separately and collapsed per minute (AUDIT-FLOOD), one upsert per refusal (4A).
 *
 * <p>EP-09d review M-1: an unauthenticated client can cause these writes at will, and all anonymous refusals of an
 * area and minute update one row. At most {@value #MAX_CONCURRENT_WRITES} writes run at a time; a refusal that
 * finds them busy is not written (counted, logged at most once per {@value #SKIP_LOG_EVERY} skips), so audit never
 * holds more than that many pooled connections or request threads. The answer never depends on the audit.
 */
final class UrlDenialAudit {

    private static final Logger log = LoggerFactory.getLogger(UrlDenialAudit.class);

    static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    static final String CSRF_REJECTED = "CSRF_REJECTED";
    static final int MAX_CONCURRENT_WRITES = 2;
    static final long SKIP_LOG_EVERY = 1000;
    private static final UrlPathHelper PATHS = new UrlPathHelper();

    private final AuditTrail audit;
    private final SessionIdentities identities;
    private final Semaphore writes = new Semaphore(MAX_CONCURRENT_WRITES);
    private final AtomicLong skipped = new AtomicLong();

    UrlDenialAudit(AuditTrail audit, SessionIdentities identities) {
        this.audit = audit;
        this.identities = identities;
    }

    /** No authenticated session: no actor (all anonymous refusals of an area and minute share one row). */
    void unauthenticated(HttpServletRequest request) {
        record(request, UNAUTHENTICATED, () -> null);
    }

    /** A role not allowed for the URL, or a CSRF failure; the actor is the session's when there is one. */
    void forbidden(HttpServletRequest request, AccessDeniedException exception) {
        String outcome = exception instanceof CsrfException ? CSRF_REJECTED : AuditEvent.FORBIDDEN;
        record(request, outcome, () -> identities.current().map(SessionIdentity::auditActor).orElse(null));
    }

    private void record(HttpServletRequest request, String outcome, Supplier<AuditActor> actor) {
        try {
            AuditEvent.Resource area = area(request);
            log.info("url.denied code={} area={}", outcome, area);
            if (!writes.tryAcquire()) {
                long count = skipped.incrementAndGet();
                if (count % SKIP_LOG_EVERY == 1) {
                    log.warn("url.denied audit_skipped reason=BUSY total={}", count);
                }
                return;
            }
            try {
                audit.recordSeparately(AuditEvent.denied(area, outcome, actor.get()));
            } finally {
                writes.release();
            }
        } catch (RuntimeException failure) {
            // The refusal stands whatever happens here (review L-4: both paths); only the class is logged.
            log.error("url.denied not_recorded exception={}", failure.getClass().getSimpleName());
        }
    }

    /**
     * The area of the URL, from the decoded path inside the application (no query string, no path parameters), as
     * the authorization rules see it (review L-1: {@code /api/%6fps/...} is OPS).
     */
    static AuditEvent.Resource area(HttpServletRequest request) {
        String path = PATHS.getPathWithinApplication(request);
        if (path == null) {
            return AuditEvent.Resource.ENDPOINT_OTHER;
        }
        if (under(path, "/api/internal")) {
            return AuditEvent.Resource.ENDPOINT_INTERNAL;
        }
        if (under(path, "/api/ops")) {
            return AuditEvent.Resource.ENDPOINT_OPS;
        }
        if (under(path, "/api/demo/security")) {
            return AuditEvent.Resource.ENDPOINT_SECURITY;
        }
        if (under(path, "/api/demo")) {
            return AuditEvent.Resource.ENDPOINT_DEMO;
        }
        return AuditEvent.Resource.ENDPOINT_OTHER;
    }

    private static boolean under(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}
