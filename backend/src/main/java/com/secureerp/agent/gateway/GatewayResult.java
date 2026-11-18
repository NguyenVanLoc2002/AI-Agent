package com.secureerp.agent.gateway;

import com.secureerp.agent.gateway.api.ModelResponse;
import java.util.Objects;

/** Outcome of a gateway call. Refusals and failures carry a reason code only, never content. */
public sealed interface GatewayResult {

    /** The provider answered and the answer passed strict parsing. Still untrusted (AI_GATEWAY §4). */
    record Completed(ModelResponse response) implements GatewayResult {

        public Completed {
            Objects.requireNonNull(response, "response");
        }
    }

    /** Nothing was sent. The caller must hand off to staff with this reason. */
    record Refused(RefusalReason reason) implements GatewayResult {

        public Refused {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** The request passed egress checks but the provider call did not produce a usable answer. */
    record Failed(FailureReason reason) implements GatewayResult {

        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }

    enum RefusalReason {
        /** The egress policy blocked the payload, or could not decide (fail-closed). */
        DLP_EGRESS,
        /** The egress decision could not be audited, so nothing was sent. */
        AUDIT_UNAVAILABLE
    }

    enum FailureReason {
        /** Timeout, I/O error, 5xx after bounded retries, or no provider configured. */
        PROVIDER_UNAVAILABLE,
        /** 401/403: key revoked or invalid (SEC-07 handoff reason PROVIDER_AUTH). */
        PROVIDER_AUTH,
        /** Other 4xx from the provider. */
        PROVIDER_REJECTED,
        /** The response was oversized, malformed or outside the response schema. */
        INVALID_PROVIDER_RESPONSE
    }
}
