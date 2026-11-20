package com.secureerp.agent.identity;

import com.secureerp.agent.identity.spi.ErpCustomerLink;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test-only access to the identity factories (same package); production code cannot do this. */
public final class IdentityFixtures {

    /** Synthetic customers and the Mock ERP keys they are linked to (A-19; MockErpBackend). */
    public static final Map<String, String> SYNTHETIC_ERP_LINKS =
            Map.of("SynthCustomerA", "SYNTH-CUST-A", "SynthCustomerB", "SYNTH-CUST-B", "SynthCustomerC", "SYNTH-CUST-C");

    public static final Team TEAM_1 = new Team("team-1");
    public static final Team TEAM_2 = new Team("team-2");

    private IdentityFixtures() {
    }

    /**
     * Binds the deterministic synthetic customers in {@code customer_link} with plain SQL (idempotent);
     * test contexts call it at startup. Tests that need their own lock/limit scope bind a fresh reference
     * through {@link CustomerLinkBinding} instead.
     */
    public static void linkSyntheticCustomers(JdbcTemplate jdbc) {
        SYNTHETIC_ERP_LINKS.forEach((seed, erpKey) -> jdbc.update(
                "insert into customer_link (customer_ref, erp_link, linked_at) values (?, ?, ?) "
                        + "on conflict (customer_ref) do nothing",
                customer(seed).opaqueValue(), new ErpCustomerLink(erpKey).value(),
                Timestamp.from(Instant.parse("2026-11-18T00:00:00Z"))));
    }

    /** A synthetic principal: {@code usr_} plus the seed padded to 22 base64url characters. */
    public static PrincipalId principal(String seed) {
        return PrincipalId.fromAuthenticatedSession("usr_" + pad(seed));
    }

    /** A synthetic customer reference: {@code cref_} plus the seed padded to 22 base64url characters. */
    public static AuthenticatedCustomerRef customer(String seed) {
        return AuthenticatedCustomerRef.fromVerifiedBinding("cref_" + pad(seed));
    }

    /** A customer session identity (web chat) for unit tests of session consumers (EP-04). */
    public static SessionIdentity sessionIdentity(String principalSeed, String customerSeed) {
        return new SessionIdentity(principal(principalSeed), Role.CUST, Channel.WEB_CHAT, customer(customerSeed),
                TEAM_1);
    }

    /** A linked customer no team serves (EP-08a, A-42(e)). */
    public static SessionIdentity unassignedCustomer(String principalSeed, String customerSeed) {
        return new SessionIdentity(principal(principalSeed), Role.CUST, Channel.WEB_CHAT, customer(customerSeed), null);
    }

    /** A staff session identity (no customer) for unit and integration tests of staff APIs (EP-07). */
    public static SessionIdentity staffIdentity(String principalSeed, Role role) {
        return new SessionIdentity(principal(principalSeed), role, Channel.WEB_CHAT, null,
                role == Role.SALES ? TEAM_1 : null);
    }

    /** A SALES session identity of the given team (EP-08). */
    public static SessionIdentity salesIdentity(String principalSeed, Team team) {
        return new SessionIdentity(principal(principalSeed), Role.SALES, Channel.WEB_CHAT, null, team);
    }

    static String pad(String seed) {
        String padded = (seed + "ZZZZZZZZZZZZZZZZZZZZZZ").substring(0, 22);
        if (!padded.matches("[A-Za-z0-9_-]{22}")) {
            throw new IllegalArgumentException("seed must be base64url characters");
        }
        return padded;
    }
}
