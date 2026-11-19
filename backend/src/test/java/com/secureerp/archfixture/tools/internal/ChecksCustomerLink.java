package com.secureerp.archfixture.tools.internal;

import com.secureerp.archfixture.identity.AuthenticatedCustomerRef;
import com.secureerp.archfixture.identity.CustomerLinks;

/** ARCH-08e violation: the tool layer asks whether a customer is linked. */
public class ChecksCustomerLink {

    public boolean linked(CustomerLinks links, AuthenticatedCustomerRef customer) {
        return links.erpLinkOf(customer) != null;
    }
}
