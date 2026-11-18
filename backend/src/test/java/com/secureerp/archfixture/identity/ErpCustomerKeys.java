package com.secureerp.archfixture.identity;

/** Fixture stand-in for the restricted ERP key accessor (ARCH-08d). */
public final class ErpCustomerKeys {

    private ErpCustomerKeys() {
    }

    public static String keyOf(AuthenticatedCustomerRef customer) {
        return "cref_fixture";
    }
}
