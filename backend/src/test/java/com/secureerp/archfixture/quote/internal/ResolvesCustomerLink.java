package com.secureerp.archfixture.quote.internal;

import com.secureerp.archfixture.identity.AuthenticatedCustomerRef;
import com.secureerp.archfixture.identity.CustomerLinks;

/** ARCH-08e compliant: quote resolves the ERP customer to price. */
public class ResolvesCustomerLink {

    public Object erpCustomer(CustomerLinks links, AuthenticatedCustomerRef customer) {
        return links.erpLinkOf(customer);
    }
}
