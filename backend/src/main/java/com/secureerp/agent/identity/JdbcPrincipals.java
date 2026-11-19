package com.secureerp.agent.identity;

import com.secureerp.agent.identity.spi.ChannelIdentity;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stable pseudonymous principal per channel user ({@code principal_binding}, Flyway V8; A-37). Package
 * private: only identity creates a {@link PrincipalId}. The reference is {@code usr_} plus 128 random
 * bits, unrelated to the user name or any ERP key.
 */
@Component
class JdbcPrincipals {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    JdbcPrincipals(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** The principal of this channel user, created on first login; concurrent logins get the same one. */
    PrincipalId findOrCreate(ChannelIdentity binding) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        jdbc.update("insert into principal_binding (principal_ref, channel, provider_user_hash, created_at) "
                        + "values (?, ?, ?, ?) on conflict (channel, provider_user_hash) do nothing",
                "usr_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), binding.channel(),
                binding.providerUserHash(), Timestamp.from(clock.instant()));
        return find(binding).orElseThrow(() -> new IllegalStateException("principal not stored"));
    }

    Optional<PrincipalId> find(ChannelIdentity binding) {
        return jdbc.queryForList(
                        "select principal_ref from principal_binding where channel = ? and provider_user_hash = ?",
                        String.class, binding.channel(), binding.providerUserHash())
                .stream().map(PrincipalId::fromAuthenticatedSession).findFirst();
    }
}
