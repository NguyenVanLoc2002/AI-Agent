package com.secureerp.agent.gateway.adapter;

import com.secureerp.agent.gateway.api.ResponseBudget;
import com.secureerp.agent.gateway.internal.EgressSerializer;
import com.secureerp.agent.gateway.internal.ModelProviderPort;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HTTP transport to the Fake provider (ADR-004, A-05). Uses the JDK HTTP client; no provider SDK.
 *
 * <p>The wire format is the gateway's own JSON ({@code ModelRequest} out, {@code ModelResponse} in)
 * posted to {@code /v1/fake-provider/complete}. It is not any vendor's API: no real provider is
 * approved (D-07). No credentials are sent. Redirects are never followed.
 *
 * <p>Hardening after the SEC-01a security review:
 * <ul>
 *   <li>the proxy selector is always explicit: the configured loopback proxy, otherwise
 *       {@link HttpClient.Builder#NO_PROXY}. JVM-wide proxy settings ({@code -Dhttp.proxyHost},
 *       system proxies) can therefore never route egress elsewhere (H1);</li>
 *   <li>one deadline covers headers and body, and the body is capped while it streams, so a
 *       slowly dripping or oversized answer cannot pin the caller (M4);</li>
 *   <li>an interrupt is reported as {@code CANCELLED}, which the gateway never retries (L2).</li>
 * </ul>
 */
@Component
public class HttpModelProviderAdapter implements ModelProviderPort {

    static final String COMPLETE_PATH = "/v1/fake-provider/complete";

    private final Optional<URI> endpoint;
    private final HttpClient client;
    private final Duration shortTimeout;
    private final Duration standardTimeout;
    private final List<EgressCaptureListener> captureListeners;

    public HttpModelProviderAdapter(
            @Value("${apas.gateway.provider.base-url:}") String baseUrl,
            @Value("${apas.gateway.provider.proxy:}") String proxy,
            @Value("${apas.gateway.provider.connect-timeout:2s}") Duration connectTimeout,
            @Value("${apas.gateway.provider.timeout.short:5s}") Duration shortTimeout,
            @Value("${apas.gateway.provider.timeout.standard:15s}") Duration standardTimeout,
            ObjectProvider<EgressCaptureListener> captureListeners) {
        this.endpoint = ProviderEndpointPolicy.baseUri(baseUrl).map(uri -> uri.resolve(COMPLETE_PATH));
        Optional<InetSocketAddress> proxyAddress = ProviderEndpointPolicy.proxy(proxy);
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connectTimeout)
                .proxy(proxyAddress.map(ProxySelector::of).orElse(HttpClient.Builder.NO_PROXY))
                .build();
        this.shortTimeout = shortTimeout;
        this.standardTimeout = standardTimeout;
        this.captureListeners = captureListeners.orderedStream().toList();
    }

    /** For tests: the configured client (proxy selector must never be the JVM default). */
    HttpClient client() {
        return client;
    }

    @Override
    public ProviderReply send(byte[] requestBody, ResponseBudget budget) throws ProviderTransportException {
        if (endpoint.isEmpty()) {
            throw new ProviderTransportException(ProviderTransportException.Kind.NOT_CONFIGURED);
        }
        Duration deadline = budget == ResponseBudget.SHORT ? shortTimeout : standardTimeout;
        HttpRequest request = HttpRequest.newBuilder(endpoint.get())
                .timeout(deadline)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build();
        captureListeners.forEach(listener -> listener.onAttempt(requestBody.clone()));
        CompletableFuture<HttpResponse<byte[]>> exchange =
                client.sendAsync(request, info -> new CappedBody(EgressSerializer.MAX_RESPONSE_BYTES));
        try {
            HttpResponse<byte[]> response = exchange.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
            return new ProviderReply(response.statusCode(), response.body());
        } catch (TimeoutException e) {
            exchange.cancel(true);
            throw new ProviderTransportException(ProviderTransportException.Kind.TIMEOUT);
        } catch (InterruptedException e) {
            exchange.cancel(true);
            Thread.currentThread().interrupt();
            throw new ProviderTransportException(ProviderTransportException.Kind.CANCELLED);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ResponseTooLargeException) {
                throw new ProviderTransportException(ProviderTransportException.Kind.RESPONSE_TOO_LARGE);
            }
            if (cause instanceof HttpTimeoutException) {
                throw new ProviderTransportException(ProviderTransportException.Kind.TIMEOUT);
            }
            throw new ProviderTransportException(ProviderTransportException.Kind.IO);
        }
    }

    /** Signals that the body exceeded the cap; carries no content. */
    static final class ResponseTooLargeException extends IOException {

        ResponseTooLargeException() {
            super("RESPONSE_TOO_LARGE", null);
        }
    }

    /** Collects at most {@code limit} bytes, then cancels the stream. */
    static final class CappedBody implements HttpResponse.BodySubscriber<byte[]> {

        private final int limit;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        CappedBody(int limit) {
            this.limit = limit;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            for (ByteBuffer item : items) {
                if (buffer.size() + item.remaining() > limit) {
                    subscription.cancel();
                    result.completeExceptionally(new ResponseTooLargeException());
                    return;
                }
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                buffer.writeBytes(chunk);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(buffer.toByteArray());
        }
    }
}
