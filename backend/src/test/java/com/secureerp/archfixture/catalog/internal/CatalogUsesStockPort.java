package com.secureerp.archfixture.catalog.internal;

import com.secureerp.archfixture.quote.spi.AvailabilityPort;

/** Violation ARCH-12 (and ARCH-02a): catalog reads exact stock quantity. */
public class CatalogUsesStockPort {
    public int stock(AvailabilityPort port) {
        return port.quantityOnHand("SKU-SYNTH-1");
    }
}
