package com.secureerp.agent.identity;

import com.secureerp.agent.audit.AuditActor;
import java.util.Objects;
import java.util.Optional;

/**
 * Who is calling, resolved on the server from the authenticated session (ADR-010, rule 10): the
 * pseudonymous principal, the role, the channel and, for a customer whose channel binding is linked to
 * the ERP, the customer reference (A-37). Never built from request text or tool arguments; only
 * identity creates one ({@link CurrentIdentity}).
 */
public final class SessionIdentity {

    private final PrincipalId principal;
    private final Role role;
    private final Channel channel;
    private final AuthenticatedCustomerRef customer;
    private final Team team;

    SessionIdentity(PrincipalId principal, Role role, Channel channel, AuthenticatedCustomerRef customer, Team team) {
        this.principal = Objects.requireNonNull(principal, "principal");
        this.role = Objects.requireNonNull(role, "role");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.customer = customer;
        this.team = team;
    }

    public PrincipalId principal() {
        return principal;
    }

    public Role role() {
        return role;
    }

    public Channel channel() {
        return channel;
    }

    /** The linked customer; empty for staff and for a customer the ERP does not know (D-04). */
    public Optional<AuthenticatedCustomerRef> customer() {
        return Optional.ofNullable(customer);
    }

    /**
     * EP-08 (D-08 PoC default): for SALES the team they belong to, for a customer the team that serves
     * them; empty for every other role and when none is configured (then nobody is assigned: fail closed).
     */
    public Optional<Team> team() {
        return Optional.ofNullable(team);
    }

    /** The pseudonym and role for {@code audit_event} (EP-09b); never a name or a customer key. */
    public AuditActor auditActor() {
        return new AuditActor(principal.pseudonym(), role.name());
    }

    @Override
    public String toString() {
        return "SessionIdentity[" + role + ", " + channel + ", redacted]";
    }
}
