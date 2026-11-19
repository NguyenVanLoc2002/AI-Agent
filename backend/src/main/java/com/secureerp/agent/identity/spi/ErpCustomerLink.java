package com.secureerp.agent.identity.spi;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The ERP's own customer key, as the ERP link lookup returned it (A-19 APPROVED 2026-11-18). RESTRICTED:
 * it never leaves identity, quote and the erp adapter (ARCH-08d), and {@link #toString()} is redacted.
 * Its format is the ERP's (D-01 OPEN): it is deliberately unrelated to {@code AuthenticatedCustomerRef},
 * which is a random server reference.
 */
public record ErpCustomerLink(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");

    public ErpCustomerLink {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid erp customer link format");
        }
    }

    @Override
    public String toString() {
        return "ErpCustomerLink[redacted]";
    }
}
