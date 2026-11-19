package com.secureerp.agent.agent;

/**
 * The agent loop (DATA_FLOW §1 steps 4–11): builds the policy-safe {@code ModelRequest}, calls the AI
 * gateway, runs tool proposals through the tool manager, and validates the final text. It never sees
 * prices, the customer reference value or quote cards; it returns a typed {@link AgentReply}.
 *
 * <p>Call outside a database transaction (the tool manager and the gateway audit write in their own).
 */
public interface Agent {

    AgentReply reply(AgentTurn turn);
}
