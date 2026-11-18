package com.secureerp.agent.catalog.spi;

/**
 * Coarse availability for one SKU (ERP_CONNECTOR §3.4, A-12 APPROVED 2026-11-18). Deliberately takes
 * no requested quantity, so repeated calls cannot be used as a stock oracle (D-12).
 */
public interface AvailabilityBucketPort {

    AvailabilityBucket bucket(Sku sku);
}
