package com.secureerp.agent.tools.spi;

import com.secureerp.agent.identity.AuthenticatedCustomerRef;
import com.secureerp.agent.identity.PrincipalId;
import com.secureerp.agent.platform.ConversationId;
import java.util.Optional;

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

    /**
     * The customer the conversation was bound to when it was created (A-35 APPROVED 2026-11-19). The
     * tool layer quotes only when it equals {@code ToolCallContext.customer}, so a wrong wiring cannot
     * price for another customer (SEC-02). Default: no binding known, so no quote (fail closed).
     */
    default Optional<AuthenticatedCustomerRef> boundCustomer(PrincipalId principal, ConversationId conversation) {
        return Optional.empty();
    }
}
