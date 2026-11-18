package com.secureerp.agent.catalog.internal;

import com.secureerp.agent.catalog.CatalogMatch;
import com.secureerp.agent.catalog.CatalogSearch;
import com.secureerp.agent.catalog.spi.AvailabilityBucketPort;
import com.secureerp.agent.catalog.spi.ExactProductQuery;
import com.secureerp.agent.catalog.spi.ProductCandidate;
import com.secureerp.agent.catalog.spi.Provenance;
import com.secureerp.agent.catalog.spi.VerifiedProductPort;
import com.secureerp.agent.platform.ConversationId;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Delegates to the ERP-backed ports; caps the result size again on this side of the boundary. */
@Service
class DefaultCatalogSearch implements CatalogSearch {

    private final VerifiedProductPort products;
    private final AvailabilityBucketPort availability;
    private final ConversationVinStore vins;
    private final Clock clock;

    DefaultCatalogSearch(VerifiedProductPort products, AvailabilityBucketPort availability, ConversationVinStore vins,
            Clock clock) {
        this.products = products;
        this.availability = availability;
        this.vins = vins;
        this.clock = clock;
    }

    /**
     * {@link ExactProductQuery.ByVin} is refused here: a VIN query is built only from the
     * conversation's stored VIN ({@link #searchByConversationVin}), never by a caller (EP-05
     * architecture review M2).
     */
    @Override
    public List<CatalogMatch> search(ExactProductQuery query) {
        Objects.requireNonNull(query, "query");
        if (query instanceof ExactProductQuery.ByVin) {
            throw new IllegalArgumentException("vin queries only through searchByConversationVin");
        }
        return lookup(query);
    }

    @Override
    public Optional<List<CatalogMatch>> searchByConversationVin(ConversationId conversation) {
        Objects.requireNonNull(conversation, "conversation");
        return vins.current(conversation, clock.instant())
                .map(vin -> lookup(new ExactProductQuery.ByVin(vin)).stream()
                        .map(DefaultCatalogSearch::capAtCatalogLookup)
                        .toList());
    }

    private List<CatalogMatch> lookup(ExactProductQuery query) {
        return products.search(query).stream()
                .limit(ExactProductQuery.MAX_RESULTS)
                .map(candidate -> new CatalogMatch(candidate, availability.bucket(candidate.sku())))
                .toList();
    }

    /**
     * A VIN narrows the catalog to a vehicle model; it does not identify the exact part. A VIN result
     * is therefore at most CATALOG_LOOKUP (BR-06), whatever the adapter reports; enforced here, not
     * only in the Mock adapter (EP-05 architecture review M1).
     */
    private static CatalogMatch capAtCatalogLookup(CatalogMatch match) {
        ProductCandidate c = match.candidate();
        if (c.provenance() != Provenance.VERIFIED) {
            return match;
        }
        return new CatalogMatch(new ProductCandidate(c.sku(), c.oe(), c.category(), Provenance.CATALOG_LOOKUP,
                c.sourceRef()), match.availability());
    }
}
