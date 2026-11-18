package com.secureerp.agent.identity;

/**
 * The ERP-side customer key behind an {@link AuthenticatedCustomerRef} (A-19 APPROVED 2026-11-18:
 * the ERP link result is opaque and identity wraps it; EP-06). The ERP adapter needs the key back to
 * ask the ERP for this customer's price and credit status.
 *
 * <p>RESTRICTED. Only {@code erp} (and {@code identity}) may call it: ARCH-08d fails the build for
 * any other caller, so a business module cannot read or log the key.
 */
public final class ErpCustomerKeys {

    private ErpCustomerKeys() {
    }

    public static String keyOf(AuthenticatedCustomerRef customer) {
        return customer.opaqueValue();
    }
}
