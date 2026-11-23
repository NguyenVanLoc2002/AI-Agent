package com.secureerp.agent.quote.internal;

import com.secureerp.agent.platform.ConversationId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

/** Quote persistence (Flyway V6). */
interface QuoteStore {

    /**
     * A-34 limits of one request, per customer across conversations (owner 2026-11-19): a business
     * refusal ({@link NotQuotedReason#locksAndRetires()}) after {@code lockSince} locks the customer; at
     * most {@code maxIssued} ISSUED quotes (lost races included) after {@code issuedSince}.
     */
    record Limits(Instant lockSince, Instant issuedSince, int maxIssued) {
    }

    enum IssueResult {
        ISSUED,
        /** A newer quote was issued in this conversation while this request was checked. */
        STALE,
        /** The customer is locked after a business refusal (A-34). */
        LOCKED,
        /** The customer's issue limit is reached (A-34). */
        LIMIT,
        /** The conversation holds another customer's ISSUED quote (security review 2 L-1). */
        FOREIGN
    }

    /**
     * A-34 check before any ERP call, for this customer ({@code customerDigest}; null = no customer,
     * never limited here because no ERP call follows).
     */
    Optional<NotQuotedReason> gate(String customerDigest, Limits limits);

    /**
     * In its own transaction under the customer and conversation locks: if the reason locks
     * ({@link NotQuotedReason#locksAndRetires()}), retire this customer's ISSUED quote in the conversation
     * that is not newer than {@code now} and revoke its handle (A-33); then write the NOT_QUOTED audit row.
     * A refusal that locks the customer opens the conversation's handoff in the same transaction
     * (A-43(s)); if that fails, the refusal is still stored alone. The reason is for staff only (A-14).
     */
    void recordNotQuoted(ConversationId conversation, String customerDigest, NotQuotedReason reason, int lineCount,
            Instant now);

    /**
     * In one transaction under the customer and conversation locks: FOREIGN if the conversation holds
     * another customer's ISSUED quote, STALE if a newer ISSUED quote exists, LOCKED or LIMIT if A-34 now
     * forbids it; otherwise retire the older ISSUED quote (SUPERSEDED, or EXPIRED when past its validity),
     * revoke its handles, and insert the new quote, its lines, its handle hash and the ISSUED audit row.
     */
    IssueResult issue(NewQuote quote, Limits limits);

    Optional<StoredHandle> findHandle(String tokenHash);

    List<StoredLine> lines(long quoteId);

    /** {@code toString()} is redacted: amounts, digests and hashes never reach a log line (security L-2). */
    record NewQuote(String publicRef, ConversationId conversation, String customerDigest, Currency currency,
            int moneyScale, RoundingMode rounding, QuoteCalculator.Calculation calculation, String handleHash,
            Instant issuedAt, Instant validUntil) {

        @Override
        public String toString() {
            return "NewQuote[redacted]";
        }
    }

    /**
     * @param conversationId the handle's conversation; empty when it differs from the quote's own
     * @param customerDigest the handle's customer digest; empty when it differs from the quote's own
     */
    record StoredHandle(long quoteId, String publicRef, int quoteVersion, int handleVersion, String status,
            String conversationId, String customerDigest, Instant handleExpiresAt, Instant revokedAt,
            Instant validUntil, Currency currency, int moneyScale, BigDecimal total) {

        @Override
        public String toString() {
            return "StoredHandle[redacted]";
        }
    }

    record StoredLine(String sku, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {

        @Override
        public String toString() {
            return "StoredLine[redacted]";
        }
    }
}
