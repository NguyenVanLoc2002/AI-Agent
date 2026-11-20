package com.secureerp.agent.platform;

import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * The pseudonymous, server-generated correlation ID of the current request (set by the platform's
 * correlation filter, never taken from the client). Audit records carry it so that the security
 * inspector can group what one request did (EP-09b).
 */
public final class TraceIds {

    /** MDC key under which the correlation filter keeps the trace ID. */
    public static final String MDC_KEY = "traceId";
    public static final Pattern FORMAT =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private TraceIds() {
    }

    /** The current request's trace ID; empty outside a request or when the value is not well formed. */
    public static Optional<String> current() {
        return Optional.ofNullable(MDC.get(MDC_KEY)).filter(id -> FORMAT.matcher(id).matches());
    }
}
