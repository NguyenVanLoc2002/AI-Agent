package com.secureerp.archfixture.quote.internal;

import com.secureerp.archfixture.erp.MockAritoAdapter;

/** Violation ARCH-03: module depends directly on the erp adapter. */
public class QuoteUsesErpAdapter {
    public long price() {
        return new MockAritoAdapter().priceFor("SKU-SYNTH-1");
    }
}
