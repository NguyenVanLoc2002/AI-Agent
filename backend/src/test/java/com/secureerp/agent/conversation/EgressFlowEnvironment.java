package com.secureerp.agent.conversation;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.secureerp.agent.catalog.spi.ExactProductQuery;
import com.secureerp.agent.catalog.spi.ProductCandidate;
import com.secureerp.agent.catalog.spi.Provenance;
import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.catalog.spi.VerifiedProductPort;
import com.secureerp.agent.erp.internal.MockErpConnector;
import com.secureerp.agent.erp.internal.MockErpFaultMode;
import com.secureerp.agent.gateway.adapter.EgressCaptureListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * SEC-01b topology (ADR-016) on the real flow: HTTP chat (profile demo) → conversation → agent → tools →
 * gateway → adapter → WireMock forward proxy (loopback) → WireMock Fake provider (loopback). Three captures
 * are compared: (a) the adapter's {@link EgressCaptureListener}, (b') what the proxy forwarded, (b) what the
 * provider received.
 *
 * <p>The catalog port can be switched per conversation to an ERP failure carrying sentinels, an ERP timeout
 * or a poisoned catalog row. It is a test double placed in front of the Mock ERP: everything behind the
 * port (catalog search, tool manager, agent, gateway) is the production code.
 */
@Import(EgressFlowEnvironment.FlowConfiguration.class)
public abstract class EgressFlowEnvironment extends ChatEnvironment {

    protected static final WireMockServer PROXY =
            new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort().enableBrowserProxying(true));

    static {
        PROXY.start();
        Runtime.getRuntime().addShutdownHook(new Thread(PROXY::stop));
    }

    @DynamicPropertySource
    static void proxy(DynamicPropertyRegistry registry) {
        registry.add("apas.gateway.provider.proxy", () -> "127.0.0.1:" + PROXY.port());
    }

    @Autowired
    protected AdapterCapture adapterCapture;

    @Autowired
    protected SwitchableCatalog catalog;

    @BeforeEach
    void resetEgressCapture() {
        PROXY.resetRequests();
        adapterCapture.clear();
        catalog.use(CatalogMode.NORMAL);
    }

    /** The next provider call answers 503 once, then the scripted turns continue (retry path, rule 30 §7). */
    protected static void failNextProviderCallOnce() {
        PROVIDER.stubFor(post(urlEqualTo(COMPLETE_PATH)).atPriority(1).inScenario("retry-once")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
    }

    /** Capture (b'): bodies the local proxy forwarded, oldest first. */
    protected static List<byte[]> proxyBodies() {
        return PROXY.getAllServeEvents().stream()
                .sorted(Comparator.comparing(e -> e.getRequest().getLoggedDate()))
                .map(ServeEvent::getRequest)
                .map(request -> request.getBody())
                .toList();
    }

    /** Capture (b) as bytes, oldest first. */
    protected static List<byte[]> providerRawBodies() {
        return PROVIDER.getAllServeEvents().stream()
                .sorted(Comparator.comparing(e -> e.getRequest().getLoggedDate()))
                .map(ServeEvent::getRequest)
                .map(request -> request.getBody())
                .toList();
    }

    public enum CatalogMode {
        NORMAL,
        /** Mock ERP fails with a message carrying cost/debt sentinels and a honeytoken name. */
        ERROR_WITH_SENTINEL,
        /** Mock ERP times out; the timeout message carries a sentinel. */
        TIMEOUT,
        /** The catalog returns a row whose category or OE carries a sentinel in a format the checks accept. */
        POISONED
    }

    /** Catalog port in front of the Mock ERP; the mode is set by the test before each conversation. */
    public static final class SwitchableCatalog implements VerifiedProductPort {

        private final MockErpConnector normal;
        private final MockErpConnector failing = new MockErpConnector(MockErpFaultMode.ERROR_WITH_SENTINEL);
        private final MockErpConnector timingOut = new MockErpConnector(MockErpFaultMode.TIMEOUT);
        private volatile CatalogMode mode = CatalogMode.NORMAL;
        private volatile String poisonedCategory = "Ma phanh truoc";
        private volatile String poisonedOe = "04465-0K290";

        SwitchableCatalog(MockErpConnector normal) {
            this.normal = Objects.requireNonNull(normal, "normal");
        }

        public void use(CatalogMode mode) {
            this.mode = Objects.requireNonNull(mode, "mode");
        }

        /** A row whose category carries the given text (letters only pass the format checks). */
        public void poisonWith(String category) {
            this.poisonedCategory = Objects.requireNonNull(category, "category");
            this.poisonedOe = "04465-0K290";
            this.mode = CatalogMode.POISONED;
        }

        /** A row whose OE code carries the given text ({@code [A-Z0-9.-]} pass the format checks). */
        public void poisonOe(String oe) {
            this.poisonedCategory = "Ma phanh truoc";
            this.poisonedOe = Objects.requireNonNull(oe, "oe");
            this.mode = CatalogMode.POISONED;
        }

        @Override
        public List<ProductCandidate> search(ExactProductQuery query) {
            return switch (mode) {
                case NORMAL -> normal.search(query);
                case ERROR_WITH_SENTINEL -> failing.search(query);
                case TIMEOUT -> timingOut.search(query);
                case POISONED -> List.of(new ProductCandidate(new Sku("SKU-BRK-PAD-A"), poisonedOe,
                        poisonedCategory, Provenance.VERIFIED, "mock-erp:catalog:poisoned"));
            };
        }
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
    static class FlowConfiguration {

        @Bean
        AdapterCapture flowAdapterCapture() {
            return new AdapterCapture();
        }

        @Bean
        @Primary
        SwitchableCatalog switchableCatalog(MockErpConnector mockErp) {
            return new SwitchableCatalog(mockErp);
        }
    }
}
