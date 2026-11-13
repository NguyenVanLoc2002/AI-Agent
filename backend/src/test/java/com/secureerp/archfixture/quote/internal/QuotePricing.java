package com.secureerp.archfixture.quote.internal;

import com.secureerp.archfixture.quote.spi.PricingPort;

/** Compliant ARCH-02a: quote may use quote.spi. */
public class QuotePricing {
    public long price(PricingPort port) {
        return port.priceFor("SKU-SYNTH-1");
    }
}
