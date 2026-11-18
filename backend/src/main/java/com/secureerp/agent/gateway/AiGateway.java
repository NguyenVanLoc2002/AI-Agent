package com.secureerp.agent.gateway;

import com.secureerp.agent.gateway.api.ModelRequest;

/**
 * The only way to call a model provider (ADR-004 ACCEPTED, AI_GATEWAY §2). Each call runs the
 * egress pipeline (serialize → field allowlist → final-bytes scan → audit → send) before any byte
 * leaves. A-05: the PoC talks only to a Fake provider on a loopback address.
 */
public interface AiGateway {

    GatewayResult complete(ModelRequest request);
}
