package com.secureerp.agent.identity.adapter.web;

import com.secureerp.agent.identity.CurrentIdentity;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ErrorCode;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session transport for the web demo (ADR-010). The CSRF token is handed to the client here; it must be
 * sent as a header on every state-changing request, including login. The session view shows only the
 * role and whether a customer is linked, never a reference or an ERP value.
 */
@RestController
@RequestMapping("/api/auth")
class SessionController {

    private final CurrentIdentity identity;

    SessionController(CurrentIdentity identity) {
        this.identity = identity;
    }

    @GetMapping("/csrf")
    CsrfView csrf(CsrfToken token) {
        return new CsrfView(token.getHeaderName(), token.getToken());
    }

    @GetMapping("/session")
    SessionView session() {
        return identity.current()
                .map(current -> new SessionView(current.role(), current.customer().isPresent()))
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    record CsrfView(String headerName, String token) {
    }

    record SessionView(Role role, boolean customerLinked) {
    }
}
