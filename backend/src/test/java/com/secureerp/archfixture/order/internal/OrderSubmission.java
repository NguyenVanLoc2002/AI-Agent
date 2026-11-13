package com.secureerp.archfixture.order.internal;

import com.secureerp.archfixture.order.spi.DraftOrderPort;

/** Compliant ARCH-02b: order may use order.spi. */
public class OrderSubmission {
    public String submit(DraftOrderPort port) {
        return port.create("q1:1");
    }
}
