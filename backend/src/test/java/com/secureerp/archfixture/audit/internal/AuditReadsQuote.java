package com.secureerp.archfixture.audit.internal;

import com.secureerp.archfixture.quote.internal.QuotePricing;

/** Violation ARCH-10: audit depends on a business module. */
public class AuditReadsQuote {
    public Object peek(QuotePricing pricing) {
        return pricing;
    }
}
