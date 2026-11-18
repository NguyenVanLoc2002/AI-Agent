package com.secureerp.archfixture.identity.internal;

import com.secureerp.archfixture.identity.AuthenticatedCustomerRef;

/** Compliant: identity creates the reference. */
public class CustomerLinkResolver {

    public AuthenticatedCustomerRef resolve() {
        return new AuthenticatedCustomerRef("cref_synthetic_fixture");
    }
}
