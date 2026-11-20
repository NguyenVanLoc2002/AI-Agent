package com.secureerp.agent.securityinspector.adapter.web;

import com.secureerp.agent.identity.CurrentIdentity;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.securityinspector.SecurityInspector;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/demo/security/**} (SYSTEM_ARCHITECTURE §8, BRD §12 path for traces; decision 5A). Transport
 * only: identity from the session, role checked by the service and at the URL (SECREV only). Profile
 * {@code demo} only (ADR-015).
 */
@RestController
@Profile("demo")
@RequestMapping(path = "/api/demo/security", produces = MediaType.APPLICATION_JSON_VALUE)
class SecurityInspectorController {

    private static final int MAX_PARAMETER = 40;

    private final CurrentIdentity identity;
    private final SecurityInspector inspector;

    SecurityInspectorController(CurrentIdentity identity, SecurityInspector inspector) {
        this.identity = identity;
        this.inspector = inspector;
    }

    @GetMapping("/traces/{traceId}")
    SecurityInspector.Trace trace(@PathVariable String traceId) {
        return inspector.trace(session(), traceId);
    }

    @GetMapping("/events")
    List<SecurityInspector.EventView> events(@RequestParam(required = false) String type,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer limit) {
        if (type != null && type.length() > MAX_PARAMETER) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        return inspector.events(session(), new SecurityInspector.EventQuery(type, instant(from), instant(to), limit));
    }

    private static Instant instant(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() > MAX_PARAMETER) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException malformed) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private SessionIdentity session() {
        return identity.current().orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }
}
