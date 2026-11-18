package com.secureerp.agent.tools;

import com.secureerp.agent.identity.Channel;
import com.secureerp.agent.identity.PrincipalId;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.platform.ConversationId;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Server-side facts about one tool call (TOOL_CONTRACTS §1): who is calling, with which role, on
 * which channel, in which conversation and turn. Built by the caller from the authenticated session
 * (EP-04), never from model output or tool arguments. The tool layer still checks conversation
 * ownership itself on every call.
 *
 * @param turnId pseudonymous trace ID of the current turn (UUID); scopes the per-turn call limit
 */
public record ToolCallContext(PrincipalId principal, Role role, Channel channel, ConversationId conversation,
        String turnId) {

    private static final Pattern TURN_ID =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    public ToolCallContext {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(conversation, "conversation");
        Objects.requireNonNull(turnId, "turnId");
        if (!TURN_ID.matcher(turnId).matches()) {
            throw new IllegalArgumentException("invalid turn id format");
        }
    }
}
