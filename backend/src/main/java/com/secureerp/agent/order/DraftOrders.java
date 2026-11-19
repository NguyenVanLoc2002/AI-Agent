package com.secureerp.agent.order;

import java.util.Optional;

/**
 * Draft orders for confirmed quotes (UC-04, DATA_FLOW §2 steps 4–6; ADR-009). One request per quote
 * reference and version (database unique constraint); the ERP is asked at most once per request and
 * never again, whatever the outcome.
 */
public interface DraftOrders {

    /**
     * Registers the request (the outbox row) inside the caller's transaction, so it exists exactly
     * when the quote's confirmation is committed, together with a digest of the submission. A second
     * registration of the same quote version is ignored. Fails when no transaction is active.
     */
    void register(DraftOrderSubmission submission);

    /**
     * Sends a registered request to the ERP if no one has yet, and returns the request's state. Only
     * one caller ever sends; every other caller, and every later call, just reads the state. A
     * request still waiting to be sent is sent only when the submission is the one that was
     * registered (its digest is compared); otherwise the call fails and nothing is sent. Must be called outside a transaction (no database transaction is held
     * during the ERP call).
     */
    DraftOrderState dispatch(DraftOrderSubmission submission);

    /**
     * The request's current state; empty when the quote version has none. Like {@link #dispatch}, it
     * first turns requests left unfinished past the submit timeout (of any quote, at most a bounded
     * number per call) into unknown outcomes, which hands their conversations to staff: there is no
     * background sweeper (A-41(l)).
     */
    Optional<DraftOrderState> stateOf(String quoteRef, int quoteVersion);
}
