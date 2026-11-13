package com.secureerp.archfixture.catalog.internal;

import com.secureerp.archfixture.catalog.AvailabilityBucketPort;

/** Compliant ARCH-12. */
public class CatalogUsesBucket {
    public String bucket(AvailabilityBucketPort port) {
        return port.bucketFor("SKU-SYNTH-1");
    }
}
