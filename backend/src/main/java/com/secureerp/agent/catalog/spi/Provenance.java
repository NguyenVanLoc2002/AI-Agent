package com.secureerp.agent.catalog.spi;

/** Where a product match came from (BR-37). In the PoC, VERIFIED means "synthetic catalog" (OPEN D-05). */
public enum Provenance {
    AI_RECOGNIZED,
    CATALOG_LOOKUP,
    VERIFIED
}
