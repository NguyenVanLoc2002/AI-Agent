package com.secureerp.agent.quote.internal;

/**
 * Internal reason codes for NOT_QUOTED (BR-11). Stored in {@code quote_request} for staff (A-14,
 * EP-08); never returned to the tool layer, the model or a log line (BR-35, rule 10).
 *
 * <p>Owner decision 2026-11-19 (A-33/A-34 refined): only reasons the ERP decided about this customer
 * ({@link #locksAndRetires()}) lock the customer for 24 h and retire the older ISSUED quote; those are
 * the answers a quantity or credit probe learns from. Technical failures, limits, refusals while
 * locked and lost races hand off but neither lock, extend a lock nor retire a quote.
 */
enum NotQuotedReason {
    NO_CUSTOMER(false),
    CREDIT_NOT_ELIGIBLE(true),
    CREDIT_REVIEW_REQUIRED(true),
    PRICE_NOT_FOUND(true),
    PRICE_CURRENCY_MISMATCH(false),
    INSUFFICIENT_STOCK(true),
    SOURCE_UNAVAILABLE(false),
    /** Calculation or persistence failed (EP-06 reviews: backend M-2, security M-3). */
    STORE_UNAVAILABLE(false),
    /** A newer quote was issued while this request was being checked (backend M-1); counts toward the limit. */
    STALE_REQUEST(false),
    /** The customer is locked after a business refusal in the last 24 h (A-34; no ERP call). */
    HANDED_OFF(false),
    /** The customer's issue limit is reached (A-34; no ERP call). */
    REISSUE_LIMIT(false),
    /** The conversation holds another customer's quote (EP-06 security review 2 L-1). */
    CONVERSATION_MISMATCH(false);

    private final boolean locksAndRetires;

    NotQuotedReason(boolean locksAndRetires) {
        this.locksAndRetires = locksAndRetires;
    }

    boolean locksAndRetires() {
        return locksAndRetires;
    }
}
