package com.secureerp.agent.tools;

import com.secureerp.agent.gateway.api.HandoffResult;
import com.secureerp.agent.gateway.api.ToolResultDto;
import java.util.Objects;

/**
 * Result of one tool call: the typed DTO the model may see (it still passes the gateway egress
 * scan), and whether the conversation must be handed to staff (quota exhausted, a forbidden
 * resource, or repeated rejections; TOOL_CONTRACTS T-TOOL-05).
 *
 * @param requestedHandoff the reason of an accepted {@code requestHandoff} call (EP-08b,
 *                         TOOL_CONTRACTS §3.4), or {@code null} for every other outcome; it is never
 *                         part of what the model sees
 */
public record ToolOutcome(ToolResultDto result, boolean handoffRequired, RequestedHandoff requestedHandoff) {

    /** The reasons the model may give for {@code requestHandoff} (TOOL_CONTRACTS §3.4). */
    public enum RequestedHandoff {
        CUSTOMER_REQUEST,
        UNSURE,
        OUT_OF_SCOPE
    }

    public ToolOutcome {
        Objects.requireNonNull(result, "result");
        if (requestedHandoff != null && (!handoffRequired || !(result instanceof HandoffResult))) {
            throw new IllegalArgumentException("an accepted handoff request is a HandoffResult and always hands off");
        }
    }

    public ToolOutcome(ToolResultDto result, boolean handoffRequired) {
        this(result, handoffRequired, null);
    }
}
