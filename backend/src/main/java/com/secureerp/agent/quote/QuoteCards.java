package com.secureerp.agent.quote;

import com.secureerp.agent.identity.AuthenticatedCustomerRef;
import com.secureerp.agent.platform.ConversationId;
import java.util.Optional;

/**
 * Server-side quote card rendering (ADR-008, T-UC03-RENDER, T-UC03-TOKEN-REPLAY). The handle is
 * resolved only when it matches the conversation, the customer and the current quote version, is
 * unexpired and not revoked, and the quote is still ISSUED. Every other case is the same empty
 * result (uniform 404 at the HTTP layer, EP-04), so a handle cannot be probed.
 */
public interface QuoteCards {

    Optional<QuoteCard> render(String quoteHandle, ConversationId conversation, AuthenticatedCustomerRef customer);
}
