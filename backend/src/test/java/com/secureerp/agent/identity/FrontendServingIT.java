package com.secureerp.agent.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.DemoHttpClient;
import com.secureerp.agent.DemoWebEnvironment;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * FE-DEPLOY (A-74, decisions 1A, 3A, 5A, 7A) against the real filter chain over HTTP: the built frontend (here the
 * synthetic fixture in {@code src/test/resources/static/}) is served for GET without a session, every answer
 * (page, asset, API, error, refusal, firewall rejection) carries the security headers, hashed assets are cached for
 * a year and {@code index.html} is not cached, other paths and methods still need a session, and no other file under
 * {@code static/} is served even with one. HSTS is not sent over plain HTTP. Also passes with the real bundle on
 * the classpath ({@code -Pwith-frontend}): the fixture's test-classes entry comes first.
 */
class FrontendServingIT extends DemoWebEnvironment {

    static final String CSP = "default-src 'none'; script-src 'self'; style-src 'self'; font-src 'self'; "
            + "img-src 'self'; connect-src 'self'; form-action 'self'; base-uri 'none'; "
            + "frame-ancestors 'none'; object-src 'none'; worker-src 'none'";

    /** Written out, not read from SecurityConfiguration: a test comparing with the same constant could not fail. */
    static final Map<String, String> EXPECTED = Map.of(
            "content-security-policy", CSP,
            "x-content-type-options", "nosniff",
            "referrer-policy", "no-referrer",
            "x-frame-options", "DENY",
            "permissions-policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
            "cross-origin-opener-policy", "same-origin",
            "cross-origin-resource-policy", "same-origin");

    private static final String FIXTURE = "FE-DEPLOY test fixture";

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5)).build();

    /** A browser-like request (Accept text/html), without a session; the path is sent as given (encoded parts too). */
    private HttpResponse<String> browser(String method, String path) {
        try {
            return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("Accept", "text/html,*/*").method(method, HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private HttpResponse<String> browserGet(String path) {
        return browser("GET", path);
    }

    static void assertSecurityHeaders(HttpResponse<String> response, String what) {
        EXPECTED.forEach((name, value) ->
                assertThat(response.headers().firstValue(name)).as(name + " on " + what).contains(value));
        assertThat(response.headers().firstValue("strict-transport-security")).as("HSTS over HTTP on " + what).isEmpty();
    }

    @Test
    void thePageIsServedWithoutASessionWithTheHeadersAndIsNotCached() {
        for (String path : new String[] {"/", "/index.html"}) {
            HttpResponse<String> page = browserGet(path);
            assertThat(page.statusCode()).as(path).isEqualTo(200);
            assertThat(page.body()).as(path).contains(FIXTURE);
            assertSecurityHeaders(page, path);
            assertThat(page.headers().firstValue("cache-control").orElse("")).as(path).contains("no-store");
        }
    }

    @Test
    void hashedAssetsAreCachedForAYearAndAMissingOneIsNot() {
        HttpResponse<String> asset = browserGet("/assets/app-fixture.js");
        assertThat(asset.statusCode()).isEqualTo(200);
        assertThat(asset.headers().firstValue("content-type").orElse("")).contains("javascript");
        assertSecurityHeaders(asset, "asset");
        String cache = asset.headers().firstValue("cache-control").orElse("");
        assertThat(cache).contains("max-age=31536000").contains("public").contains("immutable").doesNotContain("no-store");

        // Review QA 8: a 404 under /assets/ must not be cached for a year.
        HttpResponse<String> missing = browserGet("/assets/missing-0000.js");
        assertThat(missing.statusCode()).isEqualTo(404);
        assertSecurityHeaders(missing, "missing asset");
        assertThat(missing.headers().firstValue("cache-control").orElse("")).doesNotContain("immutable").contains("no-store");
    }

    @Test
    void apiAnswersRefusalsAndErrorsCarryTheHeaders() {
        DemoHttpClient anonymous = client();
        HttpResponse<String> csrf = anonymous.csrf();
        assertThat(csrf.statusCode()).isEqualTo(200);
        assertSecurityHeaders(csrf, "csrf");
        assertThat(csrf.headers().firstValue("cache-control").orElse("")).contains("no-store");

        HttpResponse<String> refused = anonymous.get("/api/ops/kill-switch");
        assertThat(refused.statusCode()).isEqualTo(401);
        assertSecurityHeaders(refused, "401");

        HttpResponse<String> forbidden = loggedIn("cust-a").get("/api/ops/kill-switch");
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertSecurityHeaders(forbidden, "403");

        HttpResponse<String> missingFavicon = browserGet("/favicon.ico");
        assertThat(missingFavicon.statusCode()).isEqualTo(404);
        assertSecurityHeaders(missingFavicon, "404");
    }

    @Test
    void firewallRejectionsCarryTheHeadersAndTheProblemShape() {
        // Review security M1 / QA 7: the firewall rejects before the chain's header writer runs.
        for (String path : new String[] {"/assets/%2e%2e/application.yml", "/a;b", "//assets/app-fixture.js"}) {
            HttpResponse<String> rejected = browserGet(path);
            assertThat(rejected.statusCode()).as(path).isEqualTo(400);
            assertSecurityHeaders(rejected, path);
            assertThat(rejected.headers().firstValue("cache-control").orElse("")).as(path).contains("no-store");
            assertThat(rejected.body()).as(path).contains("\"code\":\"MALFORMED_REQUEST\"").doesNotContain(FIXTURE)
                    .doesNotContain("spring:").doesNotContain("Exception");
        }
        // An encoded slash is rejected by Tomcat itself, before Spring: 400 without the headers (a known limit,
        // ADR-015), but nothing is served and the answer names no server or version.
        HttpResponse<String> encodedSlash = browserGet("/assets/..%2fapplication.yml");
        assertThat(encodedSlash.statusCode()).isEqualTo(400);
        assertThat(encodedSlash.body()).doesNotContain(FIXTURE).doesNotContain("spring:").doesNotContainIgnoringCase("tomcat");
    }

    @Test
    void onlyTheFrontendPathsAndOnlyGetOpenWithoutASession() {
        // 401 (not 404): the path is refused before any handler looks for a file, so opening more paths turns this red.
        for (String path : new String[] {"/other.html", "/static/index.html", "/application.yml", "/assets-other/x.js",
                "/extra.txt", "/index.html/", "/INDEX.HTML", "/favicon.ico/"}) {
            HttpResponse<String> response = browserGet(path);
            assertThat(response.statusCode()).as(path).isEqualTo(401);
            assertThat(response.body()).as(path).doesNotContain(FIXTURE).doesNotContain("spring:");
            assertSecurityHeaders(response, path);
        }
        // "/assets" itself matches the open pattern /assets/**: there is no such file, so 404 (with the headers).
        HttpResponse<String> assetsRoot = browserGet("/assets");
        assertThat(assetsRoot.statusCode()).isEqualTo(404);
        assertSecurityHeaders(assetsRoot, "/assets");
        // Writes to the frontend paths are refused (no CSRF token: 403; the path is not opened for POST either).
        DemoHttpClient anonymous = client();
        for (String path : new String[] {"/", "/index.html", "/assets/app-fixture.js"}) {
            assertThat(anonymous.postEmpty(path).statusCode()).as("POST " + path).isIn(401, 403);
        }
        // With a valid CSRF token the request passes the CSRF filter: only GET is opened, so it is refused as
        // unauthenticated (401), not handed to the file handler (405). Mutation BM4 (every method opened) gave 405.
        DemoHttpClient withToken = client();
        withToken.csrf();
        for (String path : new String[] {"/", "/index.html", "/assets/app-fixture.js"}) {
            assertThat(withToken.postEmpty(path).statusCode()).as("POST with CSRF " + path).isEqualTo(401);
        }
        // Review QA 16: the other methods are not opened either (HEAD, OPTIONS: refused before any handler).
        for (String method : new String[] {"HEAD", "OPTIONS"}) {
            assertThat(browser(method, "/index.html").statusCode()).as(method).isEqualTo(401);
        }
        for (String method : new String[] {"PUT", "DELETE", "PATCH"}) {
            assertThat(browser(method, "/index.html").statusCode()).as(method + " without CSRF").isEqualTo(403);
        }
    }

    @Test
    void noOtherStaticFileIsServedEvenWithASession() {
        // Review security L2: Spring's default mapping of every file under static/ is off.
        DemoHttpClient staff = loggedIn("sales-1");
        HttpResponse<String> extra = staff.get("/extra.txt");
        assertThat(extra.statusCode()).isEqualTo(404);
        assertThat(extra.body()).doesNotContain("must never be served");
        assertThat(staff.get("/index.html").body()).contains(FIXTURE);
    }

    @Test
    void anExpiredSessionStillGetsThePageAndThenSignsInAgain() {
        // Review security L1: a reload after the idle timeout shows the app (which then reads 401 and asks to sign in),
        // not a JSON problem answer instead of the page.
        DemoHttpClient staff = loggedIn("sales-1");
        assertThat(staff.get("/api/auth/session").statusCode()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(31));
        HttpResponse<String> page = staff.get("/");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains(FIXTURE);
        assertThat(staff.get("/api/auth/session").statusCode()).isEqualTo(401);
        // An API call with an ended session still gets the 401 problem answer.
        DemoHttpClient other = loggedIn("sales-1");
        clock.advance(Duration.ofMinutes(31));
        HttpResponse<String> api = other.get("/api/internal/handoffs");
        assertThat(api.statusCode()).isEqualTo(401);
        assertThat(api.body()).contains("\"code\":\"UNAUTHENTICATED\"");
    }

    @Test
    void theUpgradeDirectiveIsAddedOnlyForTls() {
        assertThat(SecurityConfiguration.contentSecurityPolicy(false)).isEqualTo(CSP);
        assertThat(SecurityConfiguration.contentSecurityPolicy(true)).isEqualTo(CSP + "; upgrade-insecure-requests");
    }
}
