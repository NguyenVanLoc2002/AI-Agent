package com.secureerp.archfixture.conversation.internal;

import com.secureerp.archfixture.agent.AgentApi;

/** Compliant ARCH-04. */
public class ConversationUsesAgent {
    public String turn(AgentApi agent) {
        return agent.handleTurn("x");
    }
}
