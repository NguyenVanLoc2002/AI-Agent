package com.secureerp.archfixture.quote.spi;

/** Violation ARCH-09: ERP port exposing cost. */
public interface CostLeakingPort {
    long getCostFor(String sku);
}
