package com.secureerp.archfixture.quote.internal;

import com.secureerp.archfixture.identity.AuthenticatedCustomerRef;
import com.secureerp.archfixture.identity.ErpCustomerKeys;
import java.util.function.Function;

/** ARCH-08d violation through a method reference instead of a call (EP-06 security review L-3). */
public class ReadsErpCustomerKeyByReference {

    public Function<AuthenticatedCustomerRef, String> keys() {
        return ErpCustomerKeys::keyOf;
    }
}
