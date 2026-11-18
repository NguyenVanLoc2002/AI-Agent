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

    public static final String PRODUCT_REF_PREFIX = "pr_";
    static final Pattern SKU = Pattern.compile("^[A-Z0-9][A-Z0-9-]{2,31}$");
    static final Pattern OE = Pattern.compile("^[A-Z0-9][A-Z0-9.-]{2,31}$");

    public ProductRef {
        EgressText.serverToken(productRef, "productRef", PRODUCT_REF_PREFIX);
        EgressText.matching(sku, "sku", SKU);
        EgressText.optionalMatching(oe, "oe", OE);
        EgressText.categoryLabel(category, "category");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(availabilityBucket, "availabilityBucket");
    }
}
