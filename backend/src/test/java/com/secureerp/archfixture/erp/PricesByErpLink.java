package com.secureerp.archfixture.erp;

import com.secureerp.archfixture.identity.spi.ErpCustomerLink;

/** ARCH-08d compliant: the ERP adapter uses the key to call the ERP. */
public class PricesByErpLink {

    public String key(ErpCustomerLink link) {
        return link.value();
    }
}
