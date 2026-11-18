package com.secureerp.agent.identity;

/** Test-only access to the identity factories (same package); production code cannot do this. */
public final class IdentityFixtures {

    private IdentityFixtures() {
    }

    /** A synthetic principal: {@code usr_} plus the seed padded to 22 base64url characters. */
    public static PrincipalId principal(String seed) {
        return PrincipalId.fromAuthenticatedSession("usr_" + pad(seed));
    }

    /** A synthetic customer reference: {@code cref_} plus the seed padded to 22 base64url characters. */
    public static AuthenticatedCustomerRef customer(String seed) {
        return AuthenticatedCustomerRef.fromVerifiedBinding("cref_" + pad(seed));
    }

    static String pad(String seed) {
        String padded = (seed + "ZZZZZZZZZZZZZZZZZZZZZZ").substring(0, 22);
        if (!padded.matches("[A-Za-z0-9_-]{22}")) {
            throw new IllegalArgumentException("seed must be base64url characters");
        }
        return padded;
    }
}
