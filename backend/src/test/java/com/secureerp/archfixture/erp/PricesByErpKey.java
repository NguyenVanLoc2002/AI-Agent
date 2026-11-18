package com.secureerp.archfixture.erp;

import com.secureerp.archfixture.identity.AuthenticatedCustomerRef;
import com.secureerp.archfixture.identity.ErpCustomerKeys;

/** ARCH-08d compliant: the ERP adapter reads the key to call the ERP. */
public class PricesByErpKey {

    public String key(AuthenticatedCustomerRef customer) {
        return ErpCustomerKeys.keyOf(customer);
    }
}
