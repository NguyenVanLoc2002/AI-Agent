package com.secureerp.agent.identity;

import com.secureerp.agent.identity.spi.ErpCustomerLink;
import java.util.Optional;

/**
 * Read side of the binding between the random server reference and the ERP customer (A-19 APPROVED
 * 2026-11-18). The reference carries no ERP information; the mapping lives only in identity
 * ({@code customer_link}, Flyway V7). RESTRICTED: only identity and quote may depend on it (ARCH-08e);
 * creating bindings is {@link CustomerLinkBinding}.
 */
public interface CustomerLinks {

    /** The ERP customer behind a reference; empty when unbound, revoked or {@code null} (fail closed). */
    Optional<ErpCustomerLink> erpLinkOf(AuthenticatedCustomerRef customer);
}
