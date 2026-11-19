package com.secureerp.agent.identity;

import com.secureerp.agent.identity.spi.ChannelIdentity;
import com.secureerp.agent.identity.spi.CustomerLinkPort;
import com.secureerp.agent.identity.spi.ErpCustomerLink;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the server-side identity of a session (ADR-010, A-37). At login it makes sure the channel
 * user has a stable principal and, for a customer, asks the ERP once for the link and binds it to a
 * stable reference. On every request it looks both up again from the database, so nothing in the
 * session or the request can choose them.
 *
 * <p>The web demo has one channel: every login is {@link Channel#WEB_CHAT} (Zalo is EP-12, D-06).
 */
@Component
class SessionIdentities implements CurrentIdentity, LinkedCustomers {

    private static final Logger log = LoggerFactory.getLogger(SessionIdentities.class);

    static final Channel CHANNEL = Channel.WEB_CHAT;
    static final String ROLE_PREFIX = "ROLE_";

    private final JdbcPrincipals principals;
    private final JdbcCustomerLinks links;
    private final CustomerLinkPort erpLinks;

    SessionIdentities(JdbcPrincipals principals, JdbcCustomerLinks links, CustomerLinkPort erpLinks) {
        this.principals = principals;
        this.links = links;
        this.erpLinks = erpLinks;
    }

    /**
     * Called once after a successful login. A failing ERP lookup leaves the customer unlinked for this
     * login (fail closed: no conversation, no quote) and is retried at the next login; a revoked binding
     * is never re-created. Database failures propagate (the login is refused).
     */
    void establish(String username, Role role) {
        ChannelIdentity binding = ChannelIdentity.of(CHANNEL.name(), username);
        principals.findOrCreate(binding);
        if (role != Role.CUST || links.hasBinding(binding)) {
            return;
        }
        Optional<ErpCustomerLink> link;
        try {
            link = erpLinks.findByChannelBinding(binding);
        } catch (RuntimeException e) {
            log.warn("customer_link_lookup_failed exception={}", e.getClass().getName());
            return;
        }
        link.ifPresent(erpLink -> links.bindChannel(binding, erpLink));
    }

    @Override
    public Optional<SessionIdentity> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        Optional<Role> role = roleOf(authentication);
        if (role.isEmpty()) {
            return Optional.empty();
        }
        ChannelIdentity binding = ChannelIdentity.of(CHANNEL.name(), authentication.getName());
        return principals.find(binding).map(principal -> new SessionIdentity(principal, role.get(), CHANNEL,
                role.get() == Role.CUST ? links.activeRefFor(binding).orElse(null) : null));
    }

    @Override
    public Optional<AuthenticatedCustomerRef> linkedCustomerOf(PrincipalId principal) {
        return principal == null ? Optional.empty() : links.activeRefForPrincipal(principal);
    }

    /** Exactly one {@code ROLE_*} authority naming a known role (one role per user, RBAC_MATRIX §3). */
    static Optional<Role> roleOf(Authentication authentication) {
        Optional<Role> found = Optional.empty();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String name = authority.getAuthority();
            if (name == null || !name.startsWith(ROLE_PREFIX)) {
                continue;
            }
            if (found.isPresent()) {
                return Optional.empty();
            }
            try {
                found = Optional.of(Role.valueOf(name.substring(ROLE_PREFIX.length())));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        return found;
    }
}
