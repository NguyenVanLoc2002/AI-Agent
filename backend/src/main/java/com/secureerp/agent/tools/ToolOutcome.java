package com.secureerp.agent.tools;

import com.secureerp.agent.gateway.api.ToolResultDto;
import java.util.Objects;

/**
 * Result of one tool call: the typed DTO the model may see (it still passes the gateway egress
 * scan), and whether the conversation must be handed to staff (quota exhausted, a forbidden
 * resource, or repeated rejections; TOOL_CONTRACTS T-TOOL-05).
 */
public record ToolOutcome(ToolResultDto result, boolean handoffRequired) {

    public ToolOutcome {
        Objects.requireNonNull(result, "result");
    }
}
