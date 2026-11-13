package com.secureerp.archfixture.erp;

import com.secureerp.archfixture.quote.spi.PricingPort;

/** Compliant ARCH-02a/03/09: erp implements quote.spi without forbidden methods. */
public class MockAritoAdapter implements PricingPort {
    @Override
    public long priceFor(String sku) {
        return 1L;
    }
}
