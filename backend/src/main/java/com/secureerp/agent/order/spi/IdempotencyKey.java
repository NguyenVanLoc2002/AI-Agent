package com.secureerp.agent.order.spi;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Server-derived idempotency key of one draft order (BR-17: quote reference and version). Forwarded
 * to the ERP only when the configured capability says the ERP supports it (BR-18, D-03).
 */
public record IdempotencyKey(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9:_.-]{1,64}$");

    public IdempotencyKey {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid idempotency key format");
        }
    }
}
