package com.secureerp.agent.identity.spi;

/** The ERP link lookup failed. Carries only a fixed code, no cause (rule 20: no ERP text in logs). */
public final class CustomerLinkUnavailableException extends RuntimeException {

    public CustomerLinkUnavailableException() {
        super("CUSTOMER_LINK_UNAVAILABLE", null, false, false);
    }
}
