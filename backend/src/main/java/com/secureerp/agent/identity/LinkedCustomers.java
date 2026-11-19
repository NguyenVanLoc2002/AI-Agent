package com.secureerp.agent.identity;

import java.util.Optional;

/**
 * The customer currently linked to a principal's channel binding (A-37), looked up on the server from
 * the principal alone. Used by {@code conversation} to answer which customer a conversation is bound to
 * (A-35): the conversation stores only the reference digest and compares. There is no lookup by digest,
 * ERP key or name.
 */
public interface LinkedCustomers {

    /** Empty when the principal is unknown, is not a linked customer, or the link is revoked. */
    Optional<AuthenticatedCustomerRef> linkedCustomerOf(PrincipalId principal);
}
