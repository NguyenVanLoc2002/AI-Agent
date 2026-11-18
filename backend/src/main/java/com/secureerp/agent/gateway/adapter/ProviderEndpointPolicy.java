package com.secureerp.agent.gateway.adapter;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * A-05 / ADR-004: until D-07 (provider region, ZDR/DPA, legal) is decided in writing, the gateway
 * may only reach a Fake provider on a loopback address, optionally through a loopback proxy. Any
 * other endpoint fails application startup. This is an application-level guard; production egress
 * control belongs to the network (ADR-016).
 */
final class ProviderEndpointPolicy {

    /**
     * Literal loopback addresses only. A host name such as {@code localhost} is resolved by the
     * HTTP client at request time, and a hosts-file or resolver override could point it elsewhere
     * (second review L2).
     */
    private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "[::1]", "::1");

    private ProviderEndpointPolicy() {
    }

    /** Empty when unset (gateway then fails closed with PROVIDER_UNAVAILABLE). */
    static Optional<URI> baseUri(String configured) {
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = URI.create(configured.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("apas.gateway.provider.base-url is not a valid URI");
        }
        if (!"http".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("apas.gateway.provider.base-url must be a plain http URL");
        }
        requireLoopback(uri.getHost(), "apas.gateway.provider.base-url");
        return Optional.of(uri);
    }

    static Optional<InetSocketAddress> proxy(String configured) {
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        int colon = configured.lastIndexOf(':');
        if (colon <= 0) {
            throw new IllegalStateException("apas.gateway.provider.proxy must be host:port");
        }
        String host = configured.substring(0, colon).strip();
        requireLoopback(host, "apas.gateway.provider.proxy");
        int port;
        try {
            port = Integer.parseInt(configured.substring(colon + 1).strip());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("apas.gateway.provider.proxy port is not a number");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("apas.gateway.provider.proxy port out of range");
        }
        return Optional.of(InetSocketAddress.createUnresolved(host, port));
    }

    private static void requireLoopback(String host, String property) {
        if (!LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(property + " must be a loopback address until D-07 is decided (A-05)");
        }
    }
}
