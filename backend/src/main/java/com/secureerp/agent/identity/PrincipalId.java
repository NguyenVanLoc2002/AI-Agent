package com.secureerp.agent.identity;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Pseudonymous key of the authenticated principal: {@code usr_} plus 22 base64url characters,
 * random and unrelated to any ERP or customer identifier. INTERNAL. Used for per-principal quotas
 * and audit (TOOL_CONTRACTS §1).
 *
 * <p>Only {@code identity} can create one (private marker constructor, package-private factory, as
 * for {@link AuthenticatedCustomerRef}); it is never taken from a request or a tool argument.
 * Session authentication itself is ADR-010 (PROPOSED, EP-04).
 */
public final class PrincipalId {

    private static final Pattern FORMAT = Pattern.compile("^usr_[A-Za-z0-9_-]{22}$");

    /** Proof that the caller is the factory in this package. */
    private enum Issued {
        BY_IDENTITY
    }

    private final String pseudonym;

    private PrincipalId(String pseudonym, Issued issued) {
        Objects.requireNonNull(issued, "issued");
        Objects.requireNonNull(pseudonym, "pseudonym");
        if (!FORMAT.matcher(pseudonym).matches()) {
            throw new IllegalArgumentException("invalid principal format");
        }
        this.pseudonym = pseudonym;
    }

    static PrincipalId fromAuthenticatedSession(String pseudonym) {
        return new PrincipalId(pseudonym, Issued.BY_IDENTITY);
    }

    /** Opaque key for quota and audit storage; carries no customer or ERP identifier. */
    public String pseudonym() {
        return pseudonym;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PrincipalId id && pseudonym.equals(id.pseudonym);
    }

    @Override
    public int hashCode() {
        return pseudonym.hashCode();
    }

    @Override
    public String toString() {
        return "PrincipalId[redacted]";
    }
}
