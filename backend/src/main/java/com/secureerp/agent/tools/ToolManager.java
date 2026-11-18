package com.secureerp.agent.tools;

import com.secureerp.agent.gateway.api.ToolProposal;
import com.secureerp.agent.gateway.api.ToolSpec;
import com.secureerp.agent.identity.Channel;
import com.secureerp.agent.identity.Role;
import java.util.List;

/**
 * The only way to run a tool (ADR-006 ACCEPTED, TOOL_CONTRACTS). A model tool call is a proposal;
 * every call is checked again: exact name in the allowlist for the role and channel, conversation
 * ownership, quotas, size/depth limits, strict schema, resource ownership, then execution. Every
 * outcome, success or rejection, is a typed result with a stable code; a failing quota/audit store
 * also ends as {@code TOOL_UNAVAILABLE} with a handoff.
 *
 * <p>Call it outside a database transaction: quota rows and issued productRefs are written in their
 * own transactions (they must survive a caller rollback), which needs a second pooled connection when
 * the caller already holds one (EP-03 architecture review M3).
 */
public interface ToolManager {

    /** Tool definitions offered to the model for this role and channel (may be empty). */
    List<ToolSpec> toolsFor(Role role, Channel channel);

    ToolOutcome execute(ToolCallContext context, ToolProposal proposal);
}
