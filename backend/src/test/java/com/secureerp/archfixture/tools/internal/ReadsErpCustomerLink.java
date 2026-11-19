package com.secureerp.archfixture.tools.internal;

import com.secureerp.archfixture.identity.spi.ErpCustomerLink;

/** ARCH-08d violation: the tool layer handles the ERP customer key. */
public class ReadsErpCustomerLink {

    public String key(ErpCustomerLink link) {
        return link.value();
    }
}
