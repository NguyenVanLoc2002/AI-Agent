package com.secureerp.agent.catalog.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Catalog settings.
 *
 * @param vinTtl lifetime of a conversation's current VIN (EP-05 owner decision 2026-11-18: 2 h, the
 *               same as a productRef); at most {@link #MAX_VIN_TTL}, so configuration cannot keep full
 *               VINs for long (EP-05 architecture review L6)
 */
@ConfigurationProperties("apas.catalog")
record CatalogProperties(@DefaultValue("PT2H") Duration vinTtl) {

    static final Duration MAX_VIN_TTL = Duration.ofHours(24);

    CatalogProperties {
        if (vinTtl == null || vinTtl.isNegative() || vinTtl.isZero() || vinTtl.compareTo(MAX_VIN_TTL) > 0) {
            throw new IllegalArgumentException("apas.catalog.vin-ttl must be positive and at most 24 h");
        }
    }
}
