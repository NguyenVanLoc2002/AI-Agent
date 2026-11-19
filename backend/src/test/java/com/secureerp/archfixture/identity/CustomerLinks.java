package com.secureerp.archfixture.identity;

import com.secureerp.archfixture.identity.spi.ErpCustomerLink;

/** Fixture stand-in for the customer link read side (ARCH-08e). */
public interface CustomerLinks {

    ErpCustomerLink erpLinkOf(AuthenticatedCustomerRef customer);
}
