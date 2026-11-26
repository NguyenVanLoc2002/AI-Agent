package com.secureerp.agent.identity;

import java.util.Arrays;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * FE-DEPLOY (A-74, decision 3A): the built frontend's paths, open without a session for GET only. One matcher for
 * the authorization rule ({@link SecurityConfiguration}) and the session limits ({@link SessionLimitsFilter}), so
 * the two can never disagree. {@code /favicon.ico} is listed so the browser's automatic request ends in a plain 404
 * instead of an audited URL denial (AUDIT-FLOOD). The URL prefix {@code /assets/} must match Vite's
 * {@code build.assetsDir} ({@code frontend/vite.config.ts}) and {@code platform.internal.StaticResourceConfiguration}.
 * A new public file or route needs an ADR-015 change.
 */
final class FrontendPaths {

    static final String[] PATTERNS = {"/", "/index.html", "/assets/**", "/favicon.ico"};

    static final RequestMatcher GET = new OrRequestMatcher(Arrays.stream(PATTERNS)
            .map(pattern -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, pattern))
            .toList());

    private FrontendPaths() {
    }
}
