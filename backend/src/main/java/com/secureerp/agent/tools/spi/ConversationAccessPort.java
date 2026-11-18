package com.secureerp.agent.tools.spi;

import com.secureerp.agent.identity.PrincipalId;
import com.secureerp.agent.platform.ConversationId;

/**
 * Answers whether a conversation belongs to the principal and is open for the bot (TOOL_CONTRACTS
 * §3.2: "conversation thuộc principal, không bị khóa"). Implemented by {@code conversation} in EP-04.
 * Until then no implementation exists and every tool call is refused as a forbidden resource.
 */
public interface ConversationAccessPort {

    enum Access {
        ACTIVE,
        LOCKED,
        NOT_OWNED
    }

    Access check(PrincipalId principal, ConversationId conversation);
}
