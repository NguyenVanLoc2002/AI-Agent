package com.secureerp.agent.identity;

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
