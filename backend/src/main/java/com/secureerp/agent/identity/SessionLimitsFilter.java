package com.secureerp.agent.identity;

import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.platform.ProblemResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Session rules of ADR-010 (owner 2026-11-18): a session ends after 30 minutes without a request and at
 * most 8 hours after login. Both are measured with the application {@link Clock} from attributes set at
 * login, so they are testable and do not depend on the servlet container (which also enforces the idle
 * timeout as a backstop). An ended session is invalidated and the request gets 401 with a sanitized
 * code; the client returns to the login page.
 *
 * <p>FE-DEPLOY (A-74, review security L1): a GET of the built frontend ({@link FrontendPaths}) with an ended
 * session does not get the 401 problem answer: the session is invalidated and the page is served anonymously, so
 * a reload after the idle timeout shows the app, which then reads its session (401) and asks to sign in again.
 * Every page or asset load counts as activity for the idle timeout.
 *
 * <p>Not a Spring bean on purpose: a {@code Filter} bean would also be registered as a servlet filter
 * and run twice. It is added to the security filter chain only.
 */
class SessionLimitsFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionLimitsFilter.class);

    static final String LOGIN_AT = SessionLimitsFilter.class.getName() + ".loginAt";
    static final String LAST_SEEN = SessionLimitsFilter.class.getName() + ".lastSeen";

    private final Clock clock;
    private final RequestMatcher frontend;
    private final Duration idleTimeout;
    private final Duration absoluteTimeout;

    SessionLimitsFilter(Clock clock, Duration idleTimeout, Duration absoluteTimeout) {
        this(clock, idleTimeout, absoluteTimeout, FrontendPaths.GET);
    }

    SessionLimitsFilter(Clock clock, Duration idleTimeout, Duration absoluteTimeout, RequestMatcher frontend) {
        this.clock = clock;
        this.frontend = frontend;
        this.idleTimeout = idleTimeout;
        this.absoluteTimeout = absoluteTimeout;
    }

    /** Starts the clocks of a freshly authenticated session. */
    void started(HttpSession session) {
        Instant now = clock.instant();
        session.setAttribute(LOGIN_AT, now);
        session.setAttribute(LAST_SEEN, now);
    }

    private static boolean authenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(LOGIN_AT) instanceof Instant loginAt) {
            Instant now = clock.instant();
            Instant lastSeen = session.getAttribute(LAST_SEEN) instanceof Instant seen ? seen : loginAt;
            String expired = now.isAfter(loginAt.plus(absoluteTimeout)) ? "absolute"
                    : now.isAfter(lastSeen.plus(idleTimeout)) ? "idle" : null;
            if (expired != null) {
                session.invalidate();
                SecurityContextHolder.clearContext();
                log.info("session_expired kind={}", expired);
                if (frontend.matches(request)) {
                    chain.doFilter(request, response);
                } else {
                    ProblemResponses.write(request, response, ErrorCode.UNAUTHENTICATED);
                }
                return;
            }
            session.setAttribute(LAST_SEEN, now);
        } else if (session != null && authenticated()) {
            // Fail closed (EP-04 reviews L1/L4): an authenticated session that did not start through the
            // login handler has no clocks, so it gets no unlimited lifetime either.
            session.invalidate();
            SecurityContextHolder.clearContext();
            log.warn("session_expired kind=no_start");
            ProblemResponses.write(request, response, ErrorCode.UNAUTHENTICATED);
            return;
        }
        chain.doFilter(request, response);
    }
}
