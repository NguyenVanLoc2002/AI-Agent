package com.secureerp.agent.quote.spi;

/** The ERP behind a quote port failed. Carries no message and no cause, so nothing from the ERP leaks. */
public final class QuoteSourceUnavailableException extends RuntimeException {

    public QuoteSourceUnavailableException() {
        super("QUOTE_SOURCE_UNAVAILABLE", null, false, false);
    }
}
