package com.secureerp.agent.identity.spi;

import java.util.Optional;

/**
 * ERP link lookup for an authenticated channel user (ERP_CONNECTOR §3.1, A-19/A-37). Implemented by the
 * {@code erp} adapter; called only by identity at login. It returns the ERP's customer key, never a
 * customer reference: only identity creates references (ARCH-08a/08f).
 *
 * <p>Lookup by the exact channel binding only; there is deliberately no search by name, phone number or
 * prefix (rule 10, no enumeration).
 */
public interface CustomerLinkPort {

    /**
     * The ERP customer linked to this channel user, or empty when the ERP knows no link.
     *
     * @throws CustomerLinkUnavailableException when the ERP cannot answer (no message, no cause)
     */
    Optional<ErpCustomerLink> findByChannelBinding(ChannelIdentity binding);
}
