package com.secureerp.agent.identity;

import com.secureerp.agent.identity.spi.ErpCustomerLink;

/**
 * Creates customer references (A-19). Only identity may depend on it (ARCH-08f): a reference for any ERP
 * customer is a capability, so the EP-04 channel binding flow calls it from inside identity, after
 * {@code CustomerLinkPort}, never with a value taken from a request. Whether a reference is stable per
 * channel binding is A-37 (APPROVED 2026-11-19: stable; EP-04 adds the binding columns).
 */
public interface CustomerLinkBinding {

    /** Binds a verified ERP customer to a new random reference. */
    AuthenticatedCustomerRef bind(ErpCustomerLink link);
}
