package com.secureerp.agent.gateway.internal;

import com.secureerp.agent.gateway.api.ResponseBudget;

/**
 * Transport to the model provider (AI_GATEWAY §1). Implemented only by {@code gateway.adapter}.
 * Receives the exact bytes that passed the egress scan and must send them unchanged.
 */
public interface ModelProviderPort {

    ProviderReply send(byte[] requestBody, ResponseBudget budget) throws ProviderTransportException;

    /** Raw status and body. The body is untrusted and bounded by the adapter. */
    record ProviderReply(int status, byte[] body) {
    }

    /** Timeout or I/O failure. The message is a fixed code, never the cause's message. */
    final class ProviderTransportException extends Exception {

        public enum Kind {
            TIMEOUT,
            IO,
            NOT_CONFIGURED,
            RESPONSE_TOO_LARGE,
            /** The calling thread was interrupted (e.g. kill switch); never retried. */
            CANCELLED
        }

        private final Kind kind;

        public ProviderTransportException(Kind kind) {
            super(kind.name(), null, false, false);
            this.kind = kind;
        }

        public Kind kind() {
            return kind;
        }
    }
}
