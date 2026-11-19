package com.secureerp.agent.agent;

import com.secureerp.agent.identity.AuthenticatedCustomerRef;
import com.secureerp.agent.identity.Channel;
import com.secureerp.agent.identity.PrincipalId;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.platform.ConversationId;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One customer turn handed to the agent (DATA_FLOW §1 step 4). Everything here comes from the server:
 * identity from the session, the conversation after the ownership and binding checks, texts from the
 * inbound sanitizer (A-21). {@code customerTexts} are the sanitized customer messages of the policy-safe
 * history (ADR-011 ACCEPTED 2026-11-19: last N customer turns, oldest first, the current one last);
 * there is deliberately no field for bot replies, quote cards or staff messages.
 *
 * @param maskedVin the WMI-only VIN accepted from this message (A-11), or {@code null}
 */
public record AgentTurn(PrincipalId principal, Role role, Channel channel, ConversationId conversation, String turnId,
        AuthenticatedCustomerRef customer, List<String> customerTexts, String maskedVin) {

    public static final int MAX_HISTORY = 6;

    private static final Pattern TURN_ID =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    public AgentTurn {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(conversation, "conversation");
        Objects.requireNonNull(customer, "customer");
        if (turnId == null || !TURN_ID.matcher(turnId).matches()) {
            throw new IllegalArgumentException("invalid turn id format");
        }
        customerTexts = List.copyOf(customerTexts);
        if (customerTexts.isEmpty() || customerTexts.size() > MAX_HISTORY) {
            throw new IllegalArgumentException("invalid history size");
        }
    }

    @Override
    public String toString() {
        return "AgentTurn[" + role + ", " + channel + ", turns=" + customerTexts.size() + ", redacted]";
    }
}
