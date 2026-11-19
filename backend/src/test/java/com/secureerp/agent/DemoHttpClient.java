package com.secureerp.agent;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A browser-like HTTP client for the web demo (EP-04 tests against the real filter chain on a random
 * port). The session cookie is handled by hand because {@code java.net.CookieManager} never sends a
 * {@code Secure} cookie over plain HTTP; the raw {@code Set-Cookie} header is kept so tests can check
 * the cookie flags.
 */
public final class DemoHttpClient {

    public static final String SESSION_COOKIE = "JSESSIONID";

    private static final Pattern SESSION = Pattern.compile("(?i)^" + SESSION_COOKIE + "=([^;]*)");
    private static final Pattern CSRF_HEADER = Pattern.compile("\"headerName\":\"([^\"]+)\"");
    private static final Pattern CSRF_TOKEN = Pattern.compile("\"token\":\"([^\"]+)\"");

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final URI base;
    private String sessionId;
    private String csrfHeader;
    private String csrfToken;
    private String lastSetCookie;

    public DemoHttpClient(int port) {
        this.base = URI.create("http://127.0.0.1:" + port);
    }

    public Optional<String> sessionId() {
        return Optional.ofNullable(sessionId);
    }

    /** Uses another session cookie (e.g. an old one) for the next requests. */
    public void useSession(String id) {
        this.sessionId = id;
    }

    public Optional<String> lastSetCookie() {
        return Optional.ofNullable(lastSetCookie);
    }

    /** Fetches a fresh CSRF token for this session. */
    public HttpResponse<String> csrf() {
        HttpResponse<String> response = get("/api/auth/csrf");
        Matcher header = CSRF_HEADER.matcher(response.body());
        Matcher token = CSRF_TOKEN.matcher(response.body());
        if (response.statusCode() == 200 && header.find() && token.find()) {
            csrfHeader = header.group(1);
            csrfToken = token.group(1);
        }
        return response;
    }

    public Optional<String> csrfToken() {
        return Optional.ofNullable(csrfToken);
    }

    /** Sends this token (e.g. a stale one) on the next state-changing requests. */
    public void useCsrf(String token) {
        this.csrfToken = token;
    }

    /** Forgets the CSRF token (to send a state-changing request without one). */
    public void dropCsrf() {
        csrfToken = null;
    }

    /** CSRF token, then form login. */
    public HttpResponse<String> login(String username, String password) {
        csrf();
        String form = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password="
                + URLEncoder.encode(password, StandardCharsets.UTF_8);
        HttpResponse<String> response = send(request("/api/auth/login")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)));
        if (response.statusCode() == 200) {
            csrf(); // the token rotates at login
        }
        return response;
    }

    public HttpResponse<String> get(String path) {
        return send(request(path).GET());
    }

    public HttpResponse<String> postJson(String path, String json) {
        return send(request(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    public HttpResponse<String> post(String path, String contentType, String body) {
        return send(request(path).header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    public HttpResponse<String> postEmpty(String path) {
        return send(request(path).POST(HttpRequest.BodyPublishers.noBody()));
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json, application/problem+json");
        if (sessionId != null) {
            builder.header("Cookie", SESSION_COOKIE + "=" + sessionId);
        }
        if (csrfToken != null) {
            builder.header(csrfHeader, csrfToken);
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            List<String> cookies = response.headers().allValues("Set-Cookie");
            for (String cookie : cookies) {
                Matcher matcher = SESSION.matcher(cookie);
                if (matcher.find()) {
                    lastSetCookie = cookie;
                    sessionId = matcher.group(1).isEmpty() ? null : matcher.group(1);
                }
            }
            return response;
        } catch (IOException e) {
            throw new IllegalStateException("http call failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }
}
