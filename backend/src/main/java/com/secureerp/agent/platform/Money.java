package com.secureerp.agent.platform;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount of money (ADR-014 ACCEPTED 2026-11-18). RESTRICTED when it is a customer price:
 * {@link #toString()} never prints the amount, so a price cannot reach a log line by accident, and
 * the type is not allowed in egress DTOs (ARCH-05).
 *
 * <p>Equality ignores scale ({@code 1.0 == 1.00}), as ADR-014 requires {@code compareTo}
 * semantics. Amounts are never negative in the PoC (prices and totals only); a negative ERP price
 * fails closed.
 */
public final class Money implements Comparable<Money> {

    /** Upper bound for stored amounts: NUMERIC(19,4). */
    public static final int MAX_SCALE = 4;
    private static final int MAX_PRECISION = 19;

    private final BigDecimal amount;
    private final Currency currency;

    private Money(BigDecimal amount, Currency currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public static Money of(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("money amount must not be negative");
        }
        if (amount.scale() > MAX_SCALE || amount.precision() - amount.scale() > MAX_PRECISION - MAX_SCALE) {
            throw new IllegalArgumentException("money amount out of range");
        }
        return new Money(amount, currency);
    }

    public BigDecimal amount() {
        return amount;
    }

    public Currency currency() {
        return currency;
    }

    /** Exact product (no rounding); round with {@link #rounded}. */
    public Money times(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
        return of(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return of(amount.add(other.amount), currency);
    }

    public Money rounded(int scale, RoundingMode rounding) {
        Objects.requireNonNull(rounding, "rounding");
        if (scale < 0 || scale > MAX_SCALE) {
            throw new IllegalArgumentException("scale out of range");
        }
        return of(amount.setScale(scale, rounding), currency);
    }

    public static Money zero(Currency currency) {
        return of(BigDecimal.ZERO, currency);
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("currency mismatch");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Money money && currency.equals(money.currency)
                && amount.compareTo(money.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return "Money[" + currency.getCurrencyCode() + " redacted]";
    }
}
