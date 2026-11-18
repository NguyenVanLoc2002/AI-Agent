package com.secureerp.agent.catalog;

import com.secureerp.agent.catalog.spi.AvailabilityBucket;
import com.secureerp.agent.catalog.spi.ProductCandidate;
import java.util.Objects;

/**
 * One catalog match with its availability bucket. The candidate's text fields are untrusted catalog
 * data: the tool layer validates every field again before anything reaches the model.
 */
public record CatalogMatch(ProductCandidate candidate, AvailabilityBucket availability) {

    public CatalogMatch {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(availability, "availability");
    }
}
