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
     * A-34 limits of one request: NOT_QUOTED rows after {@code lockSince} lock the conversation;
     * at most {@code maxIssued} ISSUED rows after {@code issuedSince}.
     */
    record Limits(Instant lockSince, Instant issuedSince, int maxIssued) {
    }

    enum IssueResult {
        ISSUED,
        /** A newer quote was issued while this request was checked. */
        STALE,
        /** A NOT_QUOTED inside the lock window (handed off, A-34). */
        LOCKED,
        /** The per-conversation issue limit is reached (A-34). */
        LIMIT
    }

    /** A-34 check before any ERP call: the reason the conversation may not be quoted now, if any. */
    Optional<NotQuotedReason> gate(ConversationId conversation, Limits limits);

    /**
     * In its own transaction under the conversation lock: retire the conversation's ISSUED quotes that
     * are not newer than {@code now} and revoke their handles (A-33), then write the NOT_QUOTED audit
     * row; the reason is for staff only (A-14).
     */
    void recordNotQuoted(ConversationId conversation, NotQuotedReason reason, int lineCount, Instant now);

    /**
     * In one transaction under the conversation lock: STALE if an ISSUED quote newer than
     * {@code quote.issuedAt()} exists, LOCKED or LIMIT if A-34 now forbids it; otherwise retire the
     * older ISSUED quote (SUPERSEDED, or EXPIRED when past its validity), revoke its handles, insert the
     * new quote, its lines, its handle hash and the ISSUED audit row.
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
