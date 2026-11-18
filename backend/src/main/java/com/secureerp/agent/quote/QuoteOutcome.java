package com.secureerp.agent.quote;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * What the tool layer learns about a quote request (BR-35, TOOL_CONTRACTS §3.3): an opaque handle
 * or a single NOT_QUOTED. The reason for NOT_QUOTED (stock, credit, price, source failure) is kept
 * as an internal code in {@code quote_request} for staff (A-14) and is deliberately absent here.
 */
public sealed interface QuoteOutcome {

    int lineCount();

    /** ADR-008: {@code qh_} + 22 base64url characters (128 random bits); only a SHA-256 is stored. */
    record Issued(String quoteHandle, int lineCount) implements QuoteOutcome {

        static final Pattern HANDLE = Pattern.compile("^qh_[A-Za-z0-9_-]{22}$");

        public Issued {
            Objects.requireNonNull(quoteHandle, "quoteHandle");
            if (!HANDLE.matcher(quoteHandle).matches()) {
                throw new IllegalArgumentException("invalid quote handle format");
            }
        }

        @Override
        public String toString() {
            return "Issued[lineCount=" + lineCount + "]";
        }
    }

    record NotQuoted(int lineCount) implements QuoteOutcome {
    }
}
