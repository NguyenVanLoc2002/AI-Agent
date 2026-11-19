package com.secureerp.agent.conversation.internal;

import com.secureerp.agent.identity.AuthenticatedCustomerRef;
import com.secureerp.agent.identity.LinkedCustomers;
import com.secureerp.agent.identity.PrincipalId;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.tools.spi.ConversationAccessPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The tool layer's view of a conversation (TOOL_CONTRACTS §3.2/§3.3, EP-03 port, A-35 APPROVED
 * 2026-11-19): owned by the principal and not locked, and bound to which customer. The binding is the
 * customer reference digest stored at creation; the reference returned is the principal's currently
 * linked customer only when its digest matches, so neither a revoked link nor another customer can be
 * returned. Any failure denies (the tool manager treats an exception as unavailable).
 */
@Component
class ConversationAccess implements ConversationAccessPort {

    private final ConversationStore store;
    private final LinkedCustomers customers;

    ConversationAccess(ConversationStore store, LinkedCustomers customers) {
        this.store = store;
        this.customers = customers;
    }

    @Override
    public Access check(PrincipalId principal, ConversationId conversation) {
        if (principal == null || conversation == null) {
            return Access.NOT_OWNED;
        }
        return store.findOwned(principal, conversation)
                .map(found -> found.status() == ConversationStore.Status.ACTIVE_BOT ? Access.ACTIVE : Access.LOCKED)
                .orElse(Access.NOT_OWNED);
    }

    @Override
    public Optional<AuthenticatedCustomerRef> boundCustomer(PrincipalId principal, ConversationId conversation) {
        if (principal == null || conversation == null) {
            return Optional.empty();
        }
        return store.findOwned(principal, conversation).flatMap(found -> customers.linkedCustomerOf(principal)
                .filter(customer -> customer.bindingDigest().equals(found.customerDigest())));
    }
}
