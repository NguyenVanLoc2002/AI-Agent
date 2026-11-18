package com.secureerp.archfixture.conversation.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Compliant controller: identity comes from the server-side session, not the request. */
@RestController
public class SafeChatController {

    public String chat(@RequestBody String text) {
        return text;
    }

    /** Compliant (third review M1/L5): a server-side principal and a nested body without identity. */
    public String send(Principal user, @RequestBody SafeEnvelope envelope) {
        return user.getName() + envelope.lines().size();
    }

    /** Compliant (third review L5): an exception handler may read the request; it binds no client input. */
    @ExceptionHandler(IllegalStateException.class)
    public String onError(HttpServletRequest request) {
        return request.getMethod();
    }
}
