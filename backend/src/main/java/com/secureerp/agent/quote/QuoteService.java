package com.secureerp.agent.quote;

/**
 * Automatic quoting (UC-03, ADR-007/008/014 ACCEPTED 2026-11-18). Price, stock and credit are
 * checked here, inside the protected service (BR-08); the caller learns only {@link QuoteOutcome}.
 *
 * <p>A quote is issued only when the customer is known, credit is ELIGIBLE, every line has a price
 * and enough stock (BR-11). Anything else, including an ERP failure, is the same NOT_QUOTED (BR-35).
 * One ISSUED quote per conversation: a new one supersedes the previous one and revokes its handle.
 * Database failures propagate (the caller fails closed and hands off).
 */
public interface QuoteService {

    QuoteOutcome request(QuoteRequest request);
}
