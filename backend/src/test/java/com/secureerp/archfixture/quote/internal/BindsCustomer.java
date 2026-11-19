package com.secureerp.archfixture.quote.internal;

import com.secureerp.archfixture.identity.AuthenticatedCustomerRef;
import com.secureerp.archfixture.identity.CustomerLinkBinding;
import com.secureerp.archfixture.identity.spi.ErpCustomerLink;

/** ARCH-08f violation: quote creates a reference for an arbitrary ERP customer. */
public class BindsCustomer {

    public AuthenticatedCustomerRef forge(CustomerLinkBinding binding) {
        return binding.bind(new ErpCustomerLink("SYNTH-CUST-B"));
    }
}
