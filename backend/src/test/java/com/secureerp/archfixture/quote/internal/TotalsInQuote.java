package com.secureerp.archfixture.quote.internal;

import com.secureerp.archfixture.platform.Money;

/** ARCH-13b compliant: quote owns prices and totals. */
public class TotalsInQuote {

    public Money total(Money first, Money second) {
        return new Money(first.minorUnits() + second.minorUnits());
    }
}
