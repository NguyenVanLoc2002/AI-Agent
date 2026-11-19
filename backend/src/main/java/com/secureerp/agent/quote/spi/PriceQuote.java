package com.secureerp.agent.quote.spi;

import com.secureerp.agent.platform.Money;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A customer price as the ERP reports it (ERP_CONNECTOR §3.3): unit price, where it came from and
 * its revision (re-checked at confirmation, EP-07). RESTRICTED.
 *
 * @param revision optional ({@code null}): whether the ERP versions prices is OPEN (D-01); without a
 *                 revision, EP-07 re-checks by comparing the price itself
 */
public record PriceQuote(Money unitPrice, String priceSource, String revision) {

    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9:_.-]{1,64}$");

    public PriceQuote {
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (priceSource == null || !CODE.matcher(priceSource).matches()) {
            throw new IllegalArgumentException("invalid price source format");
        }
        if (revision != null && !CODE.matcher(revision).matches()) {
            throw new IllegalArgumentException("invalid price revision format");
        }
    }

    @Override
    public String toString() {
        return "PriceQuote[redacted]";
    }
}
