package com.secureerp.archfixture.support;

import java.math.BigDecimal;

/** A foreign superclass carrying money; egress types must not inherit from it. */
public class LeakyBase {

    protected BigDecimal unitPrice;
}
