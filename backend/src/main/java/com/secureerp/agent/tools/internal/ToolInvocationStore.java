package com.secureerp.agent.tools.internal;

import com.secureerp.agent.tools.ToolCallContext;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * Quota counter and audit record of tool calls ({@code tool_invocation}, Flyway V4). Metadata only:
 * principal pseudonym, conversation, turn, registered tool name (or {@code UNKNOWN}), decision and
 * reason code. Never arguments, argument field names, results or catalog values.
 */
interface ToolInvocationStore {

    /** Registered tool names are stored as is; anything else as this marker, never the raw name. */
    String UNKNOWN_TOOL = "UNKNOWN";

    enum Decision {
        ADMITTED,
        INVOKED,
        REJECTED,
        FILTERED
    }

    /**
     * Atomically checks the quotas of this principal and records the attempt. Every attempt counts,
     * also unknown tools and invalid arguments, so probing consumes quota.
     *
     * <p>A call on a conversation the principal does not own counts against the principal only and
     * is stored without the conversation, so nobody can use up or lock another user's conversation
     * (EP-03 security review M-2).
     *
     * @return the row ID to complete, or empty when a quota is exhausted (already recorded as a
     *     {@code QUOTA_EXCEEDED} rejection)
     */
    OptionalLong admit(ToolCallContext context, String toolName, boolean ownsConversation, Instant now,
            ToolLimits limits);

    void complete(long invocationId, Decision decision, String reasonCode);

    /** Audit {@code CATALOG_FIELD_REJECTED}: catalog rows dropped before they reached the model. */
    void recordFiltered(ToolCallContext context, String toolName, Instant now);

    /** Rejected calls of this principal in this conversation since the given instant. */
    int rejectionsSince(ToolCallContext context, Instant since);
}
