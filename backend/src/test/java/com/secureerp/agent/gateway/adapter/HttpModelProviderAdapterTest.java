package com.secureerp.agent.gateway.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.secureerp.agent.gateway.api.ResponseBudget;
import com.secureerp.agent.gateway.internal.EgressSerializer;
import com.secureerp.agent.gateway.internal.ModelProviderPort.ProviderReply;
import com.secureerp.agent.gateway.internal.ModelProviderPort.ProviderTransportException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/** Adapter hardening from the SEC-01a security review: H1 (proxy), M4 (deadline/cap), L2 (cancel). */
class HttpModelProviderAdapterTest {

    private static final WireMockServer PROVIDER = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
    private static final byte[] BODY = "{\"traceId\":\"x\"}".getBytes(StandardCharsets.UTF_8);

    private final List<byte[]> captured = new ArrayList<>();

    @BeforeAll
    static void start() {
        PROVIDER.start();
    }

    @AfterAll
    static void stop() {
        PROVIDER.stop();
    }

    @BeforeEach
    void reset() {
        PROVIDER.resetAll();
    }

    private HttpModelProviderAdapter adapter(String proxy, Duration shortTimeout) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("capture", (EgressCaptureListener) captured::add);
        return new HttpModelProviderAdapter("http://127.0.0.1:" + PROVIDER.port(), proxy, Duration.ofSeconds(2),
                shortTimeout, Duration.ofSeconds(5), beans.getBeanProvider(EgressCaptureListener.class));
    }

    @Test
    void h1JvmProxySettingsAreNeverUsed() {
        HttpClient client = adapter("", Duration.ofSeconds(2)).client();
        assertThat(client.proxy()).contains(HttpClient.Builder.NO_PROXY);
        assertThat(adapter("127.0.0.1:" + PROVIDER.port(), Duration.ofSeconds(2)).client().proxy())
                .isPresent().get().isNotSameAs(HttpClient.Builder.NO_PROXY);
    }

    @Test
    void sendsExactBytesAndCapturesThem() throws Exception {
        PROVIDER.stubFor(post(urlEqualTo(HttpModelProviderAdapter.COMPLETE_PATH)).willReturn(okJson("{\"text\":\"ok\"}")));

        ProviderReply reply = adapter("", Duration.ofSeconds(2)).send(BODY, ResponseBudget.SHORT);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(captured).singleElement().isEqualTo(BODY);
        assertThat(PROVIDER.getAllServeEvents()).singleElement()
                .satisfies(e -> assertThat(e.getRequest().getBody()).isEqualTo(BODY));
    }

    @Test
    void m4SlowlyDrippingBodyHitsTheDeadline() {
        PROVIDER.stubFor(post(urlEqualTo(HttpModelProviderAdapter.COMPLETE_PATH)).willReturn(aResponse()
                .withStatus(200).withBody("{\"text\":\"" + "a".repeat(2000) + "\"}")
                .withChunkedDribbleDelay(50, 20_000)));
        long started = System.nanoTime();

        assertThatThrownBy(() -> adapter("", Duration.ofSeconds(1)).send(BODY, ResponseBudget.SHORT))
                .isInstanceOfSatisfying(ProviderTransportException.class,
                        e -> assertThat(e.kind()).isEqualTo(ProviderTransportException.Kind.TIMEOUT));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void m4OversizedBodyIsCutOff() {
        PROVIDER.stubFor(post(urlEqualTo(HttpModelProviderAdapter.COMPLETE_PATH))
                .willReturn(okJson("{\"text\":\"" + "a".repeat(EgressSerializer.MAX_RESPONSE_BYTES) + "\"}")));

        assertThatThrownBy(() -> adapter("", Duration.ofSeconds(2)).send(BODY, ResponseBudget.SHORT))
                .isInstanceOfSatisfying(ProviderTransportException.class,
                        e -> assertThat(e.kind()).isEqualTo(ProviderTransportException.Kind.RESPONSE_TOO_LARGE));
    }

    @Test
    void l2InterruptIsReportedAsCancelled() {
        PROVIDER.stubFor(post(urlEqualTo(HttpModelProviderAdapter.COMPLETE_PATH))
                .willReturn(okJson("{\"text\":\"ok\"}").withFixedDelay(3000)));
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> adapter("", Duration.ofSeconds(5)).send(BODY, ResponseBudget.SHORT))
                    .isInstanceOfSatisfying(ProviderTransportException.class,
                            e -> assertThat(e.kind()).isEqualTo(ProviderTransportException.Kind.CANCELLED));
        } finally {
            Thread.interrupted(); // clear the flag for the next test
        }
    }

    @Test
    void unconfiguredEndpointFailsClosed() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        HttpModelProviderAdapter adapter = new HttpModelProviderAdapter("", "", Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofSeconds(1), beans.getBeanProvider(EgressCaptureListener.class));
        assertThatThrownBy(() -> adapter.send(BODY, ResponseBudget.SHORT))
                .isInstanceOfSatisfying(ProviderTransportException.class,
                        e -> assertThat(e.kind()).isEqualTo(ProviderTransportException.Kind.NOT_CONFIGURED));
    }

    @Test
    void nonLoopbackEndpointFailsConstruction() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        assertThatThrownBy(() -> new HttpModelProviderAdapter("http://10.0.0.5:8080", "", Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofSeconds(1), beans.getBeanProvider(EgressCaptureListener.class)))
                .isInstanceOf(IllegalStateException.class);
    }
}
