package com.secureerp.agent.quote.internal;

/**
 * Internal reason codes for NOT_QUOTED (BR-11). Stored in {@code quote_request} for staff (A-14,
 * EP-08); never returned to the tool layer, the model or a log line (BR-35, rule 10).
 */
enum NotQuotedReason {
    NO_CUSTOMER,
    CREDIT_NOT_ELIGIBLE,
    CREDIT_REVIEW_REQUIRED,
    PRICE_NOT_FOUND,
    PRICE_CURRENCY_MISMATCH,
    INSUFFICIENT_STOCK,
    SOURCE_UNAVAILABLE,
    /** Calculation or persistence failed after the ERP checks (EP-06 reviews: backend M-2, security M-3). */
    STORE_UNAVAILABLE,
    /** A newer quote was issued in this conversation while this request was being checked (backend M-1). */
    STALE_REQUEST,
    /** A NOT_QUOTED in the last 24 h handed the conversation to staff (A-34; no ERP call). */
    HANDED_OFF,
    /** The per-conversation issue limit is reached (A-34; no ERP call). */
    REISSUE_LIMIT
}
