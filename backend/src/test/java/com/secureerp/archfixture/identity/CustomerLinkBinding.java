package com.secureerp.archfixture.identity;

import com.secureerp.archfixture.identity.spi.ErpCustomerLink;

/** Fixture stand-in for the reference-creating side (ARCH-08f). */
public interface CustomerLinkBinding {

    AuthenticatedCustomerRef bind(ErpCustomerLink link);
}
