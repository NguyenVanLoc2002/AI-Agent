package com.secureerp.agent.quote.internal;

import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.platform.Money;
import com.secureerp.agent.quote.spi.PriceQuote;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ADR-014 (owner 2026-11-18): line total = unit price × quantity, rounded per line with the
 * configured scale and rounding mode; total = sum of the rounded line totals. No VAT.
 */
final class QuoteCalculator {

    record PricedLine(Sku sku, int quantity, PriceQuote price) {

        PricedLine {
            Objects.requireNonNull(sku, "sku");
            Objects.requireNonNull(price, "price");
        }
    }

    record CalculatedLine(PricedLine line, Money lineTotal) {
    }

    record Calculation(List<CalculatedLine> lines, Money total) {
    }

    private QuoteCalculator() {
    }

    static Calculation calculate(List<PricedLine> lines, Money zero, int scale, RoundingMode rounding) {
        List<CalculatedLine> calculated = new ArrayList<>();
        Money total = zero.rounded(scale, rounding);
        for (PricedLine line : lines) {
            Money lineTotal = line.price().unitPrice().times(line.quantity()).rounded(scale, rounding);
            calculated.add(new CalculatedLine(line, lineTotal));
            total = total.plus(lineTotal);
        }
        return new Calculation(List.copyOf(calculated), total);
    }
}
