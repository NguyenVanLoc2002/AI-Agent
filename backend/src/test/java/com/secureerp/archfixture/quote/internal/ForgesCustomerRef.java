package com.secureerp.archfixture.quote.internal;

import com.secureerp.archfixture.identity.AuthenticatedCustomerRef;

/** ARCH-08a violation: a business module forges a customer reference. */
public class ForgesCustomerRef {

    public AuthenticatedCustomerRef forge(String fromRequest) {
        return new AuthenticatedCustomerRef(fromRequest);
    }
}
