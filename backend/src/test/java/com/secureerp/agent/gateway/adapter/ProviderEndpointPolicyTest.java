package com.secureerp.agent.gateway.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** A-05 / ADR-004: only a loopback Fake provider (and loopback proxy) may be configured. */
class ProviderEndpointPolicyTest {

    @Test
    void unsetEndpointIsAllowedAndMeansUnconfigured() {
        assertThat(ProviderEndpointPolicy.baseUri("")).isEmpty();
        assertThat(ProviderEndpointPolicy.baseUri(null)).isEmpty();
        assertThat(ProviderEndpointPolicy.proxy(" ")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1:8089", "http://127.0.0.1:9000/", "http://[::1]:8089"})
    void loopbackEndpointsAreAccepted(String url) {
        assertThat(ProviderEndpointPolicy.baseUri(url)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://api.provider.example", "http://10.0.0.5:8080", "http://127.0.0.1.nip.io:80",
        "http://user:pass@127.0.0.1:8089", "http://127.0.0.1:8089/?key=abc", "ftp://127.0.0.1",
        "https://127.0.0.1:8443", "not a url", "http://localhost:9000/", "http://LOCALHOST:8089"
    })
    void anyOtherEndpointFailsStartup(String url) {
        assertThatThrownBy(() -> ProviderEndpointPolicy.baseUri(url)).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("pass").hasMessageNotContaining("abc");
    }

    @ParameterizedTest
    @ValueSource(strings = {"proxy.corp.example:3128", "10.0.0.1:3128", "127.0.0.1", "127.0.0.1:0", "127.0.0.1:x",
        "localhost:18080"})
    void nonLoopbackOrMalformedProxyFailsStartup(String proxy) {
        assertThatThrownBy(() -> ProviderEndpointPolicy.proxy(proxy)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loopbackProxyIsAccepted() {
        assertThat(ProviderEndpointPolicy.proxy("127.0.0.1:18080")).isPresent();
    }
}
