package com.secureerp.archfixture.quote.spi;

/** Compliant port (ARCH-09): no cost/listing/approval methods. */
public interface PricingPort {
    long priceFor(String sku);
}
