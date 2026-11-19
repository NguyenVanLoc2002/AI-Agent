package com.secureerp.archfixture.catalog.spi;

/** Fixture stand-in for catalog.spi.VerifiedProductPort (ARCH-13c). */
public interface VerifiedProductPort {

    Sku search(String oe);
}
