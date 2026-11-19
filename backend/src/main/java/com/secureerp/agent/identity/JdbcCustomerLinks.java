package com.secureerp.agent.identity;

import com.secureerp.agent.identity.spi.ChannelIdentity;
import com.secureerp.agent.identity.spi.ErpCustomerLink;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL implementation ({@code customer_link}, Flyway V7). Lives in the identity package itself,
 * package-private, because only this package may create an {@link AuthenticatedCustomerRef}
 * (ARCH-08a; documented exception to "implementations in internal", MODULE_BOUNDARIES §10). References
 * are {@code cref_} plus 128 random bits. {@code @Component}, not {@code @Repository}: JdbcTemplate
 * already translates exceptions, and no proxy is needed.
 */
@Component
class JdbcCustomerLinks implements CustomerLinks, CustomerLinkBinding {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    JdbcCustomerLinks(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public AuthenticatedCustomerRef bind(ErpCustomerLink link) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        AuthenticatedCustomerRef customer = AuthenticatedCustomerRef.fromVerifiedBinding(
                "cref_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        // A plain insert: a (practically impossible) reference collision fails instead of silently
        // returning a reference that resolves to another customer (security review 2 L-6).
        int inserted = jdbc.update("insert into customer_link (customer_ref, erp_link, linked_at) values (?, ?, ?)",
                customer.opaqueValue(), link.value(), Timestamp.from(clock.instant()));
        if (inserted != 1) {
            throw new IllegalStateException("customer link not stored");
        }
        return customer;
    }

    /**
     * A-37 (EP-04): binds a channel user to a verified ERP customer once. Concurrent logins of the same
     * channel user end with one row (unique (channel, provider_user_hash)); the existing reference wins.
     * Empty when the binding exists but is revoked: a revoked binding is never re-created automatically.
     */
    Optional<AuthenticatedCustomerRef> bindChannel(ChannelIdentity binding, ErpCustomerLink link) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        jdbc.update("insert into customer_link (customer_ref, erp_link, linked_at, channel, provider_user_hash) "
                        + "values (?, ?, ?, ?, ?) on conflict (channel, provider_user_hash) do nothing",
                "cref_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), link.value(),
                Timestamp.from(clock.instant()), binding.channel(), binding.providerUserHash());
        return activeRefFor(binding);
    }

    /** The active (not revoked) reference of a channel user (A-37). */
    Optional<AuthenticatedCustomerRef> activeRefFor(ChannelIdentity binding) {
        return jdbc.queryForList("select customer_ref from customer_link where channel = ? and provider_user_hash = ? "
                                + "and revoked_at is null", String.class, binding.channel(), binding.providerUserHash())
                .stream().map(AuthenticatedCustomerRef::fromVerifiedBinding).findFirst();
    }

    /** The active reference of the channel user behind a principal (A-35/A-37), joined on the binding. */
    Optional<AuthenticatedCustomerRef> activeRefForPrincipal(PrincipalId principal) {
        return jdbc.queryForList("select l.customer_ref from customer_link l join principal_binding p "
                                + "on p.channel = l.channel and p.provider_user_hash = l.provider_user_hash "
                                + "where p.principal_ref = ? and l.revoked_at is null", String.class,
                        principal.pseudonym())
                .stream().map(AuthenticatedCustomerRef::fromVerifiedBinding).findFirst();
    }

    /** Whether the channel user has a binding at all, revoked or not. */
    boolean hasBinding(ChannelIdentity binding) {
        Integer count = jdbc.queryForObject(
                "select count(*) from customer_link where channel = ? and provider_user_hash = ?", Integer.class,
                binding.channel(), binding.providerUserHash());
        return count != null && count > 0;
    }

    @Override
    public Optional<ErpCustomerLink> erpLinkOf(AuthenticatedCustomerRef customer) {
        if (customer == null) {
            return Optional.empty();
        }
        return jdbc.queryForList("select erp_link from customer_link where customer_ref = ? and revoked_at is null",
                        String.class, customer.opaqueValue())
                .stream().map(ErpCustomerLink::new).findFirst();
    }
}
