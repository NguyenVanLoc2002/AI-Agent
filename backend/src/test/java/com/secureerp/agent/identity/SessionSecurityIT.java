package com.secureerp.agent.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.DemoHttpClient;
import com.secureerp.agent.DemoWebEnvironment;
import com.secureerp.agent.identity.spi.ChannelIdentity;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * ADR-010 (ACCEPTED) against the real filter chain over HTTP: T-AUTH-01 (CSRF, session fixation, user
 * switch, cookie flags), T-AUTH-IDLE, T-AUTH-MAX, and A-37 (stable references per channel binding).
 */
class SessionSecurityIT extends DemoWebEnvironment {

    private static final String WEB_CHAT = "WEB_CHAT";

    @Test
    void loginWithoutCsrfTokenIsRejected() {
        DemoHttpClient client = client();
        client.csrf();
        client.dropCsrf();
        HttpResponse<String> response = client.post("/api/auth/login", "application/x-www-form-urlencoded",
                "username=cust-a&password=" + PASSWORD);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"FORBIDDEN\"").doesNotContain(PASSWORD);
        assertThat(client.get("/api/auth/session").statusCode()).isEqualTo(401);
    }

    @Test
    void wrongPasswordOrUnknownUserIsASanitized401() {
        for (String user : new String[] {"cust-a", "nobody-here"}) {
            DemoHttpClient client = client();
            HttpResponse<String> response = client.login(user, "wrong-password-123");
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"")
                    .doesNotContain("Bad credentials").doesNotContain(user).doesNotContain("Exception");
            assertThat(client.get("/api/auth/session").statusCode()).isEqualTo(401);
        }
    }

    @Test
    void loginStartsANewSessionWithSafeCookieFlags() {
        DemoHttpClient client = client();
        client.csrf();
        String before = client.sessionId().orElseThrow();
        assertThat(client.login("cust-a", PASSWORD).statusCode()).isEqualTo(200);
        String after = client.sessionId().orElseThrow();
        assertThat(after).isNotEqualTo(before);
        String cookie = client.lastSetCookie().orElseThrow().toLowerCase(Locale.ROOT);
        assertThat(cookie).contains("httponly").contains("secure").contains("samesite=lax");

        DemoHttpClient attacker = client();
        attacker.useSession(before);
        assertThat(attacker.get("/api/auth/session").statusCode()).isEqualTo(401);
    }

    @Test
    void sessionShowsOnlyRoleAndWhetherACustomerIsLinked() {
        assertThat(loggedIn("cust-a").get("/api/auth/session").body())
                .isEqualTo("{\"role\":\"CUST\",\"customerLinked\":true}");
        assertThat(loggedIn("cust-new").get("/api/auth/session").body())
                .isEqualTo("{\"role\":\"CUST\",\"customerLinked\":false}");
        assertThat(loggedIn("sales-1").get("/api/auth/session").body())
                .isEqualTo("{\"role\":\"SALES\",\"customerLinked\":false}");
    }

    @Test
    void customerReferenceAndPrincipalAreStableAcrossLogins() {
        ChannelIdentity binding = ChannelIdentity.of(WEB_CHAT, "cust-b");
        loggedIn("cust-b");
        String principal = principalOf(binding);
        String reference = referenceOf(binding);
        loggedIn("cust-b");
        loggedIn("cust-b");
        assertThat(principalOf(binding)).isEqualTo(principal);
        assertThat(referenceOf(binding)).isEqualTo(reference);
        assertThat(jdbc.queryForObject("select count(*) from customer_link where provider_user_hash = ?",
                Integer.class, binding.providerUserHash())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select erp_link from customer_link where provider_user_hash = ?",
                String.class, binding.providerUserHash())).isEqualTo("SYNTH-CUST-B");
    }

    @Test
    void staffAndUnlinkedCustomersGetNoCustomerReference() {
        loggedIn("sales-1");
        loggedIn("cust-new");
        for (String user : new String[] {"sales-1", "cust-new"}) {
            assertThat(jdbc.queryForObject("select count(*) from customer_link where provider_user_hash = ?",
                    Integer.class, ChannelIdentity.of(WEB_CHAT, user).providerUserHash())).isZero();
        }
    }

    @Test
    void aRevokedLinkEndsAtTheNextRequestAndIsNotRecreated() {
        ChannelIdentity binding = ChannelIdentity.of(WEB_CHAT, "cust-c");
        DemoHttpClient client = loggedIn("cust-c");
        jdbc.update("update customer_link set revoked_at = ? where provider_user_hash = ?",
                Timestamp.from(START), binding.providerUserHash());
        assertThat(client.get("/api/auth/session").body()).contains("\"customerLinked\":false");
        assertThat(loggedIn("cust-c").get("/api/auth/session").body()).contains("\"customerLinked\":false");
        assertThat(jdbc.queryForObject("select count(*) from customer_link where provider_user_hash = ?",
                Integer.class, binding.providerUserHash())).isEqualTo(1);
    }

    @Test
    void theCsrfTokenRotatesAtLoginAndProtectsLogout() {
        DemoHttpClient client = client();
        client.csrf();
        String preLogin = client.csrfToken().orElseThrow();
        assertThat(client.login("cust-a", PASSWORD).statusCode()).isEqualTo(200);

        client.useCsrf(preLogin);
        assertThat(client.postEmpty("/api/auth/logout").statusCode()).isEqualTo(403);
        client.dropCsrf();
        assertThat(client.postEmpty("/api/auth/logout").statusCode()).isEqualTo(403);
        assertThat(client.get("/api/auth/session").statusCode()).isEqualTo(200);

        client.csrf();
        assertThat(client.postEmpty("/api/auth/logout").statusCode()).isEqualTo(204);
        assertThat(client.get("/api/auth/session").statusCode()).isEqualTo(401);
    }

    @Test
    void switchingUserReplacesTheSession() {
        DemoHttpClient client = loggedIn("cust-a");
        String first = client.sessionId().orElseThrow();
        assertThat(client.login("sales-1", PASSWORD).statusCode()).isEqualTo(200);
        assertThat(client.sessionId().orElseThrow()).isNotEqualTo(first);
        assertThat(client.get("/api/auth/session").body()).contains("\"role\":\"SALES\"");

        DemoHttpClient old = client();
        old.useSession(first);
        assertThat(old.get("/api/auth/session").statusCode()).isEqualTo(401);
    }

    @Test
    void sessionEndsAfterThirtyIdleMinutes() {
        DemoHttpClient client = loggedIn("cust-a");
        clock.advance(Duration.ofMinutes(29));
        assertThat(client.get("/api/auth/session").statusCode()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(29));
        assertThat(client.get("/api/auth/session").statusCode()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(31));
        HttpResponse<String> expired = client.get("/api/auth/session");
        assertThat(expired.statusCode()).isEqualTo(401);
        assertThat(expired.body()).contains("\"code\":\"UNAUTHENTICATED\"");
        assertThat(client.get("/api/auth/session").statusCode()).isEqualTo(401);
    }

    @Test
    void sessionEndsEightHoursAfterLoginEvenWhenActive() {
        DemoHttpClient client = loggedIn("cust-a");
        for (int step = 1; step <= 16; step++) {
            clock.advance(Duration.ofMinutes(29));
            assertThat(client.get("/api/auth/session").statusCode()).as("after %d min", step * 29).isEqualTo(200);
        }
        clock.advance(Duration.ofMinutes(29)); // 493 min > 8 h
        assertThat(client.get("/api/auth/session").statusCode()).isEqualTo(401);
    }

    @Test
    void anonymousRequestsGetASanitized401AndHealthStaysOpen() {
        DemoHttpClient client = client();
        HttpResponse<String> response = client.get("/api/demo/conversations/anything");
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"").doesNotContain("at com.");
        assertThat(client.get("/actuator/health").statusCode()).isEqualTo(200);
    }

    private String principalOf(ChannelIdentity binding) {
        return jdbc.queryForObject("select principal_ref from principal_binding where provider_user_hash = ?",
                String.class, binding.providerUserHash());
    }

    private String referenceOf(ChannelIdentity binding) {
        return jdbc.queryForObject("select customer_ref from customer_link where provider_user_hash = ?",
                String.class, binding.providerUserHash());
    }
}
