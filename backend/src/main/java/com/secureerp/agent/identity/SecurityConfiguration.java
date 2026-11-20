package com.secureerp.agent.identity;

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

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SessionIdentities identities, IdentityProperties properties,
            Clock clock) throws Exception {
        SessionLimitsFilter limits =
                new SessionLimitsFilter(clock, properties.idleTimeout(), properties.absoluteTimeout());
        http.authorizeHttpRequests(requests -> requests
                        // ADR-015: actuator exposes only health; other actuator paths do not exist (404).
                        .requestMatchers("/actuator/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, CSRF_URL).permitAll()
                        .requestMatchers(LOGIN_URL).permitAll()
                        // EP-08 (owner approval 2026-11-19, A-41(m)): staff endpoints are SALES only at the
                        // URL level as well; the services still check the role, the team and the resource.
                        .requestMatchers("/api/internal/**").hasRole(Role.SALES.name())
                        // EP-09a (owner approval 2026-11-20, A-44 decision 3A): the kill switch is OPS only
                        // at the URL level as well; the service checks the role again.
                        .requestMatchers("/api/ops/**").hasRole(Role.OPS.name())
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
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                ProblemResponses.write(request, response, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, exception) ->
                                ProblemResponses.write(request, response, ErrorCode.FORBIDDEN)))
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
