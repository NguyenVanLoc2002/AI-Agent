package com.secureerp.agent.identity;

import java.util.Optional;

/**
 * The identity of the current request (ADR-010). Resolved from the authenticated session on every call
 * (not cached), so a revoked customer link takes effect at the next request. Empty when the request is
 * not authenticated or the principal is unknown (fail closed).
 */
public interface CurrentIdentity {

    Optional<SessionIdentity> current();
}
