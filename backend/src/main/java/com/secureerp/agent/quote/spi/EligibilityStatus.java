package com.secureerp.agent.quote.spi;

/** Coarse credit status (ERP_CONNECTOR §3.5). Only {@link #ELIGIBLE} allows an automatic quote. */
public enum EligibilityStatus {
    ELIGIBLE,
    NOT_ELIGIBLE,
    REVIEW_REQUIRED
}
