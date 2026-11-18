package com.secureerp.agent.catalog.spi;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stock keeping unit, bounded format (ERP_CONNECTOR §2). */
public record Sku(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z0-9][A-Z0-9-]{2,31}$");

    public Sku {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid sku format");
        }
    }
}
