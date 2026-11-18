package com.secureerp.agent.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.secureerp.agent.TestcontainersConfiguration;
import com.secureerp.agent.gateway.adapter.EgressCaptureListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * ADR-016 test topology: gateway adapter → WireMock forward proxy (loopback) → WireMock Fake provider
 * (loopback). Capture (a) is the adapter's {@link EgressCaptureListener}; capture (b) is what the
 * proxy forwarded and the provider received. Synthetic data only; Postgres via Testcontainers.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, FakeProviderEnvironment.CaptureConfiguration.class})
public abstract class FakeProviderEnvironment {

    public static final String COMPLETE_PATH = "/v1/fake-provider/complete";

    protected static final WireMockServer PROVIDER =
            new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
    protected static final WireMockServer PROXY =
            new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort().enableBrowserProxying(true));

    static {
        PROVIDER.start();
        PROXY.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            PROXY.stop();
            PROVIDER.stop();
        }));
    }

    @Autowired
    protected AdapterCapture adapterCapture;

    @Autowired
    protected JdbcTemplate jdbc;

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("apas.gateway.provider.base-url", () -> "http://127.0.0.1:" + PROVIDER.port());
        registry.add("apas.gateway.provider.proxy", () -> "127.0.0.1:" + PROXY.port());
    }

    @BeforeEach
    void resetCapture() {
        PROVIDER.resetAll();
        PROXY.resetRequests();
        adapterCapture.clear();
        jdbc.update("delete from egress_record");
        PROVIDER.stubFor(post(urlEqualTo(COMPLETE_PATH))
                .willReturn(okJson("{\"text\":\"Da tim thay san pham phu hop.\"}")));
    }

    /** This trace gets one 503, then 200 (retry path of rule 30 §8). */
    protected static void failOnceFor(String traceId) {
        PROVIDER.stubFor(post(urlEqualTo(COMPLETE_PATH)).atPriority(1)
                .withRequestBody(containing(traceId))
                .inScenario("retry-" + traceId).whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
    }

    /** Capture (b): bodies that reached the Fake provider, oldest first. */
    protected static List<byte[]> providerBodies() {
        return bodies(PROVIDER);
    }

    /** Capture (b'): bodies the local proxy forwarded, oldest first. */
    protected static List<byte[]> proxyBodies() {
        return bodies(PROXY);
    }

    private static List<byte[]> bodies(WireMockServer server) {
        return server.getAllServeEvents().stream()
                .sorted(Comparator.comparing(e -> e.getRequest().getLoggedDate()))
                .map(ServeEvent::getRequest)
                .map(r -> r.getBody())
                .toList();
    }

    /** Capture (a): thread-safe record of every adapter send attempt. */
    public static final class AdapterCapture implements EgressCaptureListener {

        private final List<byte[]> attempts = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void onAttempt(byte[] requestBody) {
            attempts.add(requestBody);
        }

        public List<byte[]> attempts() {
            synchronized (attempts) {
                return List.copyOf(attempts);
            }
        }

        void clear() {
            attempts.clear();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CaptureConfiguration {

        @Bean
        AdapterCapture adapterCapture() {
            return new AdapterCapture();
        }
    }
}
