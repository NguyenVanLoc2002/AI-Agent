package com.secureerp.agent.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.identity.spi.ChannelIdentity;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** EP-04 identity building blocks: channel hash, role resolution, configuration guards. */
class IdentityUnitTest {

    @Test
    void channelIdentityHashesAndRedactsTheUser() {
        ChannelIdentity a = ChannelIdentity.of("WEB_CHAT", "Cust-A");
        assertThat(a).isEqualTo(ChannelIdentity.of("WEB_CHAT", "cust-a"));
        assertThat(a.providerUserHash()).hasSize(64).doesNotContain("cust");
        assertThat(a).isNotEqualTo(ChannelIdentity.of("ZALO", "cust-a"));
        assertThat(a.toString()).doesNotContain(a.providerUserHash()).doesNotContain("cust");
        for (String bad : new String[] {"a", "user name", "x@example.test", "0912345678'--", ""}) {
            assertThatThrownBy(() -> ChannelIdentity.of("WEB_CHAT", bad)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new ChannelIdentity("web", a.providerUserHash()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exactlyOneKnownRoleIsRequired() {
        assertThat(SessionIdentities.roleOf(auth("ROLE_CUST"))).contains(Role.CUST);
        assertThat(SessionIdentities.roleOf(auth("ROLE_SALES", "FACTOR_PASSWORD"))).contains(Role.SALES);
        assertThat(SessionIdentities.roleOf(auth())).isEmpty();
        assertThat(SessionIdentities.roleOf(auth("ROLE_ADMIN"))).isEmpty();
        assertThat(SessionIdentities.roleOf(auth("ROLE_CUST", "ROLE_SALES"))).isEmpty();
    }

    @Test
    void configurationGuards() {
        assertThatThrownBy(() -> new IdentityProperties(null, null, "short", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdentityProperties(Duration.ofHours(9), Duration.ofHours(8), "", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        IdentityProperties defaults = new IdentityProperties(null, null, null, null);
        assertThat(defaults.idleTimeout()).isEqualTo(Duration.ofMinutes(30));
        assertThat(defaults.absoluteTimeout()).isEqualTo(Duration.ofHours(8));
        IdentityProperties secret = new IdentityProperties(null, null, "a-long-demo-secret", List.of());
        assertThat(secret.toString()).doesNotContain("a-long-demo-secret");
    }

    @Test
    void sessionIdentityDoesNotPrintIdentifiers() {
        SessionIdentity identity = new SessionIdentity(IdentityFixtures.principal("PrintMe"), Role.CUST,
                Channel.WEB_CHAT, IdentityFixtures.customer("PrintMe"), IdentityFixtures.TEAM_1);
        assertThat(identity.toString()).doesNotContain("PrintMe").doesNotContain("usr_").doesNotContain("cref_");
    }

    @Test
    void anAuthenticatedSessionWithoutLoginTimeIsEnded() throws Exception {
        SessionLimitsFilter filter = new SessionLimitsFilter(Clock.systemUTC(), Duration.ofMinutes(30), Duration.ofHours(8));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/session");
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(auth("ROLE_CUST"));
        try {
            filter.doFilter(request, response, new MockFilterChain());
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHENTICATED\"");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void anAnonymousSessionPassesTheLimitsFilter() throws Exception {
        SessionLimitsFilter filter = new SessionLimitsFilter(Clock.systemUTC(), Duration.ofMinutes(30), Duration.ofHours(8));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/csrf");
        request.setSession(new MockHttpSession());
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void theDemoProfileOnlyListensOnLoopback() {
        new DemoProfileGuard("127.0.0.1");
        new DemoProfileGuard("::1");
        for (String address : new String[] {"", "0.0.0.0", "localhost", "192.168.1.10"}) {
            assertThatThrownBy(() -> new DemoProfileGuard(address)).isInstanceOf(IllegalStateException.class);
        }
    }

    private static UsernamePasswordAuthenticationToken auth(String... authorities) {
        return UsernamePasswordAuthenticationToken.authenticated("cust-a", null,
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
    }
}
