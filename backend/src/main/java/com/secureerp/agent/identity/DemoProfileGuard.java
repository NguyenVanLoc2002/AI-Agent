package com.secureerp.agent.identity;

import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Profile {@code demo} (ADR-015) has synthetic users that share one password and no login throttling
 * (UX-12 lockout is a later issue). It may therefore only listen on a literal loopback address; any other
 * bind address stops the application at startup (EP-04 security review M4). Same literal-loopback rule as
 * the gateway provider endpoint (A-05).
 */
@Component
@Profile("demo")
class DemoProfileGuard {

    static final Set<String> LOOPBACK = Set.of("127.0.0.1", "::1");

    DemoProfileGuard(@Value("${server.address:}") String address) {
        if (!LOOPBACK.contains(address)) {
            throw new IllegalStateException("profile demo requires server.address on a loopback literal");
        }
    }
}
