package com.secureerp.agent.catalog.spi;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A product match (ERP_CONNECTOR §3.2): identity and provenance only. No price, no stock number,
 * no ERP-internal fields; the adapter strips everything else at its boundary (T-ERP-02).
 */
public record ProductCandidate(Sku sku, String oe, String category, Provenance provenance, String sourceRef) {

    private static final Pattern OE = Pattern.compile("^[A-Z0-9][A-Z0-9.-]{2,31}$");
    private static final Pattern SOURCE_REF = Pattern.compile("^[a-z0-9:_-]{3,64}$");

    public ProductCandidate {
        Objects.requireNonNull(sku, "sku");
        if (oe != null && !OE.matcher(oe).matches()) {
            throw new IllegalArgumentException("invalid oe format");
        }
        if (category == null || category.isBlank() || category.length() > 80) {
            throw new IllegalArgumentException("invalid category");
        }
        Objects.requireNonNull(provenance, "provenance");
        if (sourceRef == null || !SOURCE_REF.matcher(sourceRef).matches()) {
            throw new IllegalArgumentException("invalid sourceRef format");
        }
    }
}
