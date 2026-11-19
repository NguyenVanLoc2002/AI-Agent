package com.secureerp.archfixture.tools.internal;

import com.secureerp.archfixture.platform.Money;

/** ARCH-13b violation: the tool layer handles a money amount. */
public class PricesInTools {

    public long amount(Money money) {
        return money.minorUnits();
    }
}
