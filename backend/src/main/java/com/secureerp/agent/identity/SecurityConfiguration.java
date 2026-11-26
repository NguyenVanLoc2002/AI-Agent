package com.secureerp.agent.identity;

import com.secureerp.agent.audit.AuditTrail;
import com.secureerp.agent.identity.spi.ChannelIdentity;
import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.platform.ProblemResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;

/**
 * ADR-010 (ACCEPTED; A-06 APPROVED): form login with synthetic demo users, session cookie (HttpOnly,
 * Secure, SameSite=Lax; application.yml), CSRF token on every state-changing request including login,
 * a new session at login (session fixation), 30 min idle / 8 h maximum ({@link SessionLimitsFilter}).
 * Every response for an unauthenticated or forbidden request is the sanitized problem shape.
 *
 * <p>Endpoints: {@code GET /api/auth/csrf} (token for the next state-changing request),
 * {@code POST /api/auth/login} (form fields {@code username}, {@code password}),
 * {@code POST /api/auth/logout}. Not done here (UX-12, later issue): lockout after failed logins,
 * warning before expiry, ending other sessions on password change.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(IdentityProperties.class)
class SecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

    static final String CSRF_URL = "/api/auth/csrf";
    static final String LOGIN_URL = "/api/auth/login";
    static final String LOGOUT_URL = "/api/auth/logout";
    static final String SESSION_COOKIE = "JSESSIONID";

    /**
     * FE-CSP (A-73) and FE-DEPLOY (A-74, 5A: {@code worker-src 'none'}): the same policy as
     * {@code frontend/config/securityHeaders.ts}, sent with every response (pages, assets, API answers, errors).
     */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'none'", "script-src 'self'", "style-src 'self'", "font-src 'self'", "img-src 'self'",
            "connect-src 'self'", "form-action 'self'", "base-uri 'none'", "frame-ancestors 'none'",
            "object-src 'none'", "worker-src 'none'");
    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=(), usb=()";
    /** HSTS (4A): one year, no subdomains, no preload; Spring Security sends it on HTTPS requests only. */
    static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;

    /** The policy, with {@code upgrade-insecure-requests} when the server runs with TLS (profile {@code tls}, 4A). */
    static String contentSecurityPolicy(boolean upgradeInsecureRequests) {
        return upgradeInsecureRequests ? CONTENT_SECURITY_POLICY + "; upgrade-insecure-requests" : CONTENT_SECURITY_POLICY;
    }

    /**
     * FE-DEPLOY review security M1 / QA 7: a request the firewall rejects (encoded or traversal paths, {@code ;})
     * never reaches the header writer of the chain, so its 400 answer gets the same headers here, and the sanitized
     * problem shape. Requests Tomcat itself rejects (a malformed request line) never reach Spring (ADR-015).
     */
    @Bean
    RequestRejectedHandler requestRejectedHandler(
            @Value("${apas.web.upgrade-insecure-requests:false}") boolean upgradeInsecureRequests) {
        return (request, response, rejected) -> {
            log.info("request_rejected");
            response.setHeader("Content-Security-Policy", contentSecurityPolicy(upgradeInsecureRequests));
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-Frame-Options", "DENY");
            response.setHeader("Referrer-Policy", "no-referrer");
            response.setHeader("Permissions-Policy", PERMISSIONS_POLICY);
            response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
            response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
            response.setHeader("Cache-Control", "no-store");
            ProblemResponses.write(request, response, ErrorCode.MALFORMED_REQUEST);
        };
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SessionIdentities identities, IdentityProperties properties,
            Clock clock, AuditTrail audit,
            @Value("${apas.web.upgrade-insecure-requests:false}") boolean upgradeInsecureRequests) throws Exception {
        // EP-09d (owner approval 2026-11-20, A-47): URL-level refusals are audited; the answers do not change.
        UrlDenialAudit denials = new UrlDenialAudit(audit, identities);
        SessionLimitsFilter limits =
                new SessionLimitsFilter(clock, properties.idleTimeout(), properties.absoluteTimeout());
        http.authorizeHttpRequests(requests -> requests
                        // ADR-015: actuator exposes only health; other actuator paths do not exist (404).
                        .requestMatchers("/actuator/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, CSRF_URL).permitAll()
                        .requestMatchers(LOGIN_URL).permitAll()
                        // FE-DEPLOY (owner approval 2026-11-25, A-74 decision 3A): the built frontend, GET only.
                        .requestMatchers(FrontendPaths.GET).permitAll()
                        // EP-08 (owner approval 2026-11-19, A-41(m)): staff endpoints are SALES only at the
                        // URL level as well; the services still check the role, the team and the resource.
                        .requestMatchers("/api/internal/**").hasRole(Role.SALES.name())
                        // EP-09a (owner approval 2026-11-20, A-44 decision 3A): the kill switch is OPS only
                        // at the URL level as well; the service checks the role again.
                        .requestMatchers("/api/ops/**").hasRole(Role.OPS.name())
                        // EP-09b (owner approval 2026-11-20, A-45 decision 5A): the security inspector is SECREV
                        // only at the URL level as well; the service checks the role again.
                        .requestMatchers("/api/demo/security/**").hasRole(Role.SECREV.name())
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage(LOGIN_URL)
                        .loginProcessingUrl(LOGIN_URL)
                        .successHandler((request, response, authentication) ->
                                onLogin(request, response, authentication, identities, limits))
                        .failureHandler((request, response, exception) -> {
                            log.info("login_failed");
                            ProblemResponses.write(request, response, ErrorCode.UNAUTHENTICATED);
                        }))
                .logout(logout -> logout
                        .logoutUrl(LOGOUT_URL)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies(SESSION_COOKIE)
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.newSession()))
                .requestCache(cache -> cache.disable())
                // FE-DEPLOY (A-74, 3A): security headers on every response. Spring Security's defaults stay on:
                // nosniff, X-Frame-Options DENY, Cache-Control no-store (index.html, API answers; the hashed
                // assets set their own, StaticResourceConfiguration), HSTS on HTTPS requests only.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(contentSecurityPolicy(upgradeInsecureRequests)))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY))
                        .crossOriginOpenerPolicy(coop -> coop.policy(CrossOriginOpenerPolicy.SAME_ORIGIN))
                        .crossOriginResourcePolicy(corp -> corp.policy(CrossOriginResourcePolicy.SAME_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS).includeSubDomains(false).preload(false)))
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            denials.unauthenticated(request);
                            ProblemResponses.write(request, response, ErrorCode.UNAUTHENTICATED);
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            denials.forbidden(request, exception);
                            ProblemResponses.write(request, response, ErrorCode.FORBIDDEN);
                        }))
                .addFilterAfter(limits, SecurityContextHolderFilter.class);
        return http.build();
    }

    /**
     * Synthetic demo users (decision 2026-11-19): names and roles from configuration (profile demo), one
     * password from {@code APAS_DEMO_PASSWORD}, hashed here. Without a password there is no user.
     */
    @Bean
    UserDetailsService demoUsers(IdentityProperties properties, PasswordEncoder encoder) {
        if (properties.demoPassword().isEmpty()) {
            if (!properties.demoUsers().isEmpty()) {
                log.warn("demo_login_disabled reason=no_password");
            }
            return new InMemoryUserDetailsManager();
        }
        String hash = encoder.encode(properties.demoPassword());
        Set<String> seen = new HashSet<>();
        List<UserDetails> users = new ArrayList<>();
        for (IdentityProperties.DemoUser user : properties.demoUsers()) {
            // Validates the user name format (lower case, no PII-shaped values) and rejects duplicates.
            ChannelIdentity.of(SessionIdentities.CHANNEL.name(), user.username());
            if (!seen.add(user.username())) {
                throw new IllegalArgumentException("duplicate demo user");
            }
            users.add(User.withUsername(user.username()).password(hash).roles(user.role().name()).build());
        }
        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private static void onLogin(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            SessionIdentities identities, SessionLimitsFilter limits) throws IOException {
        Optional<Role> role = SessionIdentities.roleOf(authentication);
        try {
            identities.establish(authentication.getName(), role.orElseThrow());
        } catch (RuntimeException e) {
            // Fail closed: no half-established session (the context was already saved in the new session).
            log.error("login_identity_failed exception={}", e.getClass().getName());
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            ProblemResponses.write(request, response, ErrorCode.SERVICE_UNAVAILABLE);
            return;
        }
        limits.started(request.getSession());
        boolean linked = identities.current().flatMap(SessionIdentity::customer).isPresent();
        log.info("login_succeeded role={}", role.get());
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"role\":\"" + role.get().name() + "\",\"customerLinked\":" + linked + "}");
    }
}
