package com.secureerp.agent.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;
import java.util.Objects;
import java.util.regex.Pattern;

/** A verified product as the model may see it: identity, provenance, coarse availability. No price. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record ProductRef(
        String productRef,
        String sku,
        String oe,
        String category,
        Provenance provenance,
        AvailabilityBucket availabilityBucket) {

    static final Pattern PRODUCT_REF = Pattern.compile("^pr_[A-Za-z0-9]{8,32}$");
    static final Pattern SKU = Pattern.compile("^[A-Z0-9][A-Z0-9-]{2,31}$");
    static final Pattern OE = Pattern.compile("^[A-Z0-9][A-Z0-9.-]{2,31}$");

    public ProductRef {
        EgressText.matching(productRef, "productRef", PRODUCT_REF);
        EgressText.matching(sku, "sku", SKU);
        EgressText.optionalMatching(oe, "oe", OE);
        EgressText.categoryLabel(category, "category");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(availabilityBucket, "availabilityBucket");
    }
}
