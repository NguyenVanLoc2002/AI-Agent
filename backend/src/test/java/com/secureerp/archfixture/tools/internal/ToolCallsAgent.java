package com.secureerp.archfixture.tools.internal;

import com.secureerp.archfixture.agent.AgentApi;

/** Violation ARCH-04: tools depends on agent. */
public class ToolCallsAgent {
    public String loop(AgentApi agent) {
        return agent.handleTurn("x");
    }
}
