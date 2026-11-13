package com.secureerp.agent.platform.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a pseudonymous, server-generated trace ID to every request (rule 20: correlation ID).
 *
 * <p>The trace ID is never taken from the client: a caller-chosen ID could forge or pollute
 * correlation in audit/egress records (backend-reviewer M5). An inbound {@value #HEADER} is ignored.
 * The ID is returned in the response header and kept for the error dispatch of the same request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "traceId";
    public static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Object existing = request.getAttribute(REQUEST_ATTRIBUTE);
        String traceId = existing instanceof String s ? s : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ATTRIBUTE, traceId);
        response.setHeader(HEADER, traceId);
        MDC.put(MDC_KEY, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Also run on the error dispatch so Boot's /error handling keeps the same trace ID in logs. */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
