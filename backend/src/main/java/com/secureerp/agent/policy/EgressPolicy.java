package com.secureerp.agent.policy;

/**
 * Final-bytes DLP check run by the gateway before every provider call (AI_GATEWAY §2.3–2.4).
 *
 * <p>This is the second layer. The primary boundary is the egress type system (ADR-003): a scan
 * can only prove the absence of known patterns and sentinels. Fail-closed: any exception counts as
 * a block for the caller.
 */
public interface EgressPolicy {

    EgressVerdict inspect(EgressPayload payload);
}
