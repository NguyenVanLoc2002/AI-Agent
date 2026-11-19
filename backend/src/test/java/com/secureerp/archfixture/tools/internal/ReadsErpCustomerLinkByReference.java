package com.secureerp.archfixture.tools.internal;

import com.secureerp.archfixture.identity.spi.ErpCustomerLink;
import java.util.function.Function;

/** ARCH-08d violation through a method reference instead of a call (EP-06 security review L-3). */
public class ReadsErpCustomerLinkByReference {

    public Function<ErpCustomerLink, String> keys() {
        return ErpCustomerLink::value;
    }
}
