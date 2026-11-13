package com.secureerp.archfixture.agent.internal;

import com.secureerp.archfixture.order.spi.DraftOrderPort;

/** Violation ARCH-02b: agent calls the ERP draft-order port directly. */
public class DirectOrderSubmit {
    public String submit(DraftOrderPort port) {
        return port.create("q1:1");
    }
}
