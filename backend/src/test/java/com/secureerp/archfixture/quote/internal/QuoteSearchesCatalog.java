package com.secureerp.archfixture.quote.internal;

import com.secureerp.archfixture.catalog.spi.Sku;
import com.secureerp.archfixture.catalog.spi.VerifiedProductPort;

/** ARCH-13c violation: quote searches the catalog itself instead of taking SKUs from tools. */
public class QuoteSearchesCatalog {

    public Sku find(VerifiedProductPort catalog) {
        return catalog.search("04465-0K290");
    }
}
