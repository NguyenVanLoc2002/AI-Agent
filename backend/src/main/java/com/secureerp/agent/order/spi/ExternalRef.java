package com.secureerp.agent.order.spi;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Our own reference for a draft order, sent to the ERP with the order (BRD §12 "externalRef"): the
 * quote reference and version. Staff use it to find the draft during a manual reconciliation; the
 * adapter uses it for {@link DraftOrderPort#findByExternalRef} when the ERP supports that lookup.
 * Whether the target ERP accepts such a reference is OPEN (D-01, D-03).
 */
public record ExternalRef(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9:_.-]{1,64}$");

    public ExternalRef {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid external reference format");
        }
    }
}
