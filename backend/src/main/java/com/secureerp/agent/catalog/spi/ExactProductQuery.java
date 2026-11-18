package com.secureerp.agent.catalog.spi;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Exactly one search key (ERP_CONNECTOR §3.2): an OE code, or a category plus vehicle model. No
 * free-text, prefix or wildcard search, so the catalog cannot be enumerated. Search by VIN uses a
 * VIN already validated server-side ({@link Vin}, EP-05); it is never built from model output.
 */
public sealed interface ExactProductQuery {

    /** Results are capped at this size by every adapter (DESIGN PROPOSAL). */
    int MAX_RESULTS = 5;

    record ByOe(String oe) implements ExactProductQuery {

        private static final Pattern OE = Pattern.compile("^[A-Z0-9][A-Z0-9.-]{2,31}$");

        public ByOe {
            Objects.requireNonNull(oe, "oe");
            oe = oe.strip().toUpperCase(Locale.ROOT);
            if (!OE.matcher(oe).matches()) {
                throw new IllegalArgumentException("invalid oe format");
            }
        }
    }

    /** Vehicle fitment for a validated VIN. {@code toString()} is masked through {@link Vin}. */
    record ByVin(Vin vin) implements ExactProductQuery {

        public ByVin {
            Objects.requireNonNull(vin, "vin");
        }
    }

    record ByCategoryAndModel(String category, String vehicleModel) implements ExactProductQuery {

        public ByCategoryAndModel {
            if (category == null || category.isBlank() || category.length() > 80
                    || vehicleModel == null || vehicleModel.isBlank() || vehicleModel.length() > 80) {
                throw new IllegalArgumentException("invalid category or vehicle model");
            }
            category = category.strip();
            vehicleModel = vehicleModel.strip();
        }
    }
}
