package com.secureerp.archfixture.tools.internal;

import com.secureerp.archfixture.quote.spi.PricingPort;

/** Violation ARCH-02a: a tool reads customer price directly. */
public class LeakyPricingTool {
    public long price(PricingPort port) {
        return port.priceFor("SKU-SYNTH-1");
    }
}
