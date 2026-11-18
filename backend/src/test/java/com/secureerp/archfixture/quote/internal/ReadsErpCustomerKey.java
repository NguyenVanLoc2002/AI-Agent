package com.secureerp.archfixture.quote.internal;

import com.secureerp.archfixture.identity.AuthenticatedCustomerRef;
import com.secureerp.archfixture.identity.ErpCustomerKeys;

/** ARCH-08d violation: a business module reads the ERP customer key. */
public class ReadsErpCustomerKey {

    public String key(AuthenticatedCustomerRef customer) {
        return ErpCustomerKeys.keyOf(customer);
    }
}
