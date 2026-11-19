package com.secureerp.agent.platform;

import com.secureerp.agent.platform.internal.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Writes the same RFC 9457 problem shape as the MVC error handler from places that run before MVC
 * (security filters, EP-04). Only the fixed code, title, status and the server trace ID are written;
 * never an exception message or request content (rule 10, ADR-012).
 */
public final class ProblemResponses {

    private static final Pattern TRACE_ID =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private ProblemResponses() {
    }

    public static void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.resetBuffer();
        response.setStatus(code.status().value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        StringBuilder json = new StringBuilder(160)
                .append("{\"type\":\"urn:secure-erp-agent:problem:")
                .append(code.name().toLowerCase(Locale.ROOT).replace('_', '-'))
                .append("\",\"title\":\"").append(code.title())
                .append("\",\"status\":").append(code.status().value())
                .append(",\"code\":\"").append(code.name()).append('"');
        Object traceId = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        if (traceId instanceof String id && TRACE_ID.matcher(id).matches()) {
            json.append(",\"traceId\":\"").append(id).append('"');
        }
        response.getWriter().write(json.append('}').toString());
        response.flushBuffer();
    }
}
