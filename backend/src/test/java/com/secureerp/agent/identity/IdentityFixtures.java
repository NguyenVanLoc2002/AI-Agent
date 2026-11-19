package com.secureerp.agent.identity;

import com.secureerp.agent.identity.spi.ErpCustomerLink;
import java.time.Instant;
import java.util.Map;

/** Test-only access to the identity factories (same package); production code cannot do this. */
public final class IdentityFixtures {

    /** Synthetic customers and the Mock ERP keys they are linked to (A-19; MockErpBackend). */
    public static final Map<String, String> SYNTHETIC_ERP_LINKS =
            Map.of("SynthCustomerA", "SYNTH-CUST-A", "SynthCustomerB", "SYNTH-CUST-B", "SynthCustomerC", "SYNTH-CUST-C");

    private IdentityFixtures() {
    }

    /** Binds the synthetic customers in {@code customer_link} (idempotent); test contexts call it at startup. */
    public static void linkSyntheticCustomers(CustomerLinks links) {
        JdbcCustomerLinks jdbc = (JdbcCustomerLinks) links;
        SYNTHETIC_ERP_LINKS.forEach((seed, erpKey) -> jdbc.store(customer(seed), new ErpCustomerLink(erpKey),
                Instant.parse("2026-11-18T00:00:00Z")));
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
