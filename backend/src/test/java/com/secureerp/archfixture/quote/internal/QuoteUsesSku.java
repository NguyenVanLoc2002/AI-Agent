package com.secureerp.archfixture.quote.internal;

import com.secureerp.archfixture.catalog.spi.Sku;

/** ARCH-13c compliant: quote only names the SKU type. */
public class QuoteUsesSku {

    public String code(Sku sku) {
        return sku.value();
    }
}
