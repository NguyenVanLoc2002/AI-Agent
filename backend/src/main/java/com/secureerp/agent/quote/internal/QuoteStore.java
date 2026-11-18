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

    /** Audit row for a NOT_QUOTED request in its own transaction; the reason is for staff only (A-14). */
    void recordNotQuoted(ConversationId conversation, NotQuotedReason reason, int lineCount, Instant now);

    /**
     * In one transaction under a per-conversation lock: if the conversation's ISSUED quote was issued
     * after {@code quote.issuedAt()} (a newer request won the race), change nothing and return false.
     * Otherwise retire it (SUPERSEDED, or EXPIRED when past its validity), revoke its handles, insert the
     * new quote, its lines, its handle hash and the ISSUED audit row, and return true.
     */
    boolean issue(NewQuote quote);

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
