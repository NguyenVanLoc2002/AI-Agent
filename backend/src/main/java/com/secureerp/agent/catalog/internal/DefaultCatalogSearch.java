package com.secureerp.agent.catalog.internal;

import com.secureerp.agent.catalog.CatalogMatch;
import com.secureerp.agent.catalog.CatalogSearch;
import com.secureerp.agent.catalog.spi.AvailabilityBucketPort;
import com.secureerp.agent.catalog.spi.ExactProductQuery;
import com.secureerp.agent.catalog.spi.VerifiedProductPort;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Delegates to the ERP-backed ports; caps the result size again on this side of the boundary. */
@Service
class DefaultCatalogSearch implements CatalogSearch {

    private final VerifiedProductPort products;
    private final AvailabilityBucketPort availability;

    DefaultCatalogSearch(VerifiedProductPort products, AvailabilityBucketPort availability) {
        this.products = products;
        this.availability = availability;
    }

    @Override
    public List<CatalogMatch> search(ExactProductQuery query) {
        Objects.requireNonNull(query, "query");
        return products.search(query).stream()
                .limit(ExactProductQuery.MAX_RESULTS)
                .map(candidate -> new CatalogMatch(candidate, availability.bucket(candidate.sku())))
                .toList();
    }
}
