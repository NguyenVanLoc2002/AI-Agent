package com.secureerp.agent.identity;

import com.secureerp.agent.identity.spi.ErpCustomerLink;
import java.util.Optional;

/**
 * Binding between the random server reference and the ERP customer (A-19 APPROVED 2026-11-18). The
 * reference carries no ERP information; the mapping lives only in identity ({@code customer_link},
 * Flyway V7).
 */
public interface CustomerLinks {

    /**
     * Binds a verified ERP customer to a new random reference. Called by EP-04 after the channel
     * binding lookup ({@code CustomerLinkPort}); never with a value taken from a request.
     */
    AuthenticatedCustomerRef bind(ErpCustomerLink link);

    /** The ERP customer behind a reference; empty when unbound or revoked (fail closed). */
    Optional<ErpCustomerLink> erpLinkOf(AuthenticatedCustomerRef customer);
}
