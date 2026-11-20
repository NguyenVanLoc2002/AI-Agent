package com.secureerp.agent.tools.history;

import java.time.Instant;
import java.util.List;

/** Tool calls of one chat turn, as recorded in {@code tool_invocation} (EP-03). */
public interface ToolInvocationHistory {

    /** One row: the registered tool name (or UNKNOWN), the decision and the sanitized reason code. */
    record Invocation(String toolName, String decision, String reasonCode, Instant occurredAt) {
    }

    /** Rows of the turn, oldest first. */
    List<Invocation> ofTurn(String turnId, int limit);
}
