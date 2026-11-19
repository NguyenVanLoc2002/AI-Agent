package com.secureerp.agent.quote.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.platform.Money;
import com.secureerp.agent.quote.spi.PriceQuote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** T-UC03-MONEY: per-line rounding, total = sum of rounded lines, parameterised by configuration. */
class QuoteCalculatorTest {

    private static final Currency VND = Currency.getInstance("VND");

    private static QuoteCalculator.PricedLine line(String sku, String unit, int quantity) {
        return new QuoteCalculator.PricedLine(new Sku(sku), quantity,
                new PriceQuote(Money.of(new BigDecimal(unit), VND), "mock-erp:pl:contract", "r1"));
    }

    @ParameterizedTest
    @CsvSource({
        // scale, rounding, line 1 total, line 2 total, total
        "0, HALF_UP, 1001, 3, 1004",
        "0, HALF_EVEN, 1000, 2, 1002",
        "2, HALF_UP, 1000.50, 2.50, 1003.00"
    })
    void eachLineIsRoundedThenSummed(int scale, RoundingMode rounding, String first, String second, String total) {
        QuoteCalculator.Calculation calculation = QuoteCalculator.calculate(
                List.of(line("SKU-A", "500.25", 2), line("SKU-B", "0.5", 5)), Money.zero(VND), scale, rounding);

        assertThat(calculation.lines()).extracting(l -> l.lineTotal().amount())
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal(first), new BigDecimal(second));
        assertThat(calculation.total().amount()).isEqualByComparingTo(total);
        assertThat(calculation.total().amount().scale()).isEqualTo(scale);
    }

    /** ADR-014 (owner): VND scale 0, HALF_UP, per line. Rounding the total only would give 1 less here. */
    @Test
    void approvedVndRuleRoundsPerLine() {
        QuoteCalculator.Calculation calculation = QuoteCalculator.calculate(
                List.of(line("SKU-A", "0.5", 1), line("SKU-B", "0.5", 1), line("SKU-C", "0.5", 1)),
                Money.zero(VND), 0, RoundingMode.HALF_UP);

        assertThat(calculation.total().amount()).isEqualByComparingTo("3");
    }

    @Test
    void quotePropertiesAreBounded() {
        assertThat(new QuoteProperties(VND, 0, RoundingMode.HALF_UP, Duration.ofHours(24), Duration.ofHours(24), 5).validity())
                .isEqualTo(Duration.ofHours(24));
        assertThatThrownBy(() -> new QuoteProperties(VND, 5, RoundingMode.HALF_UP, Duration.ofHours(24), Duration.ofHours(24), 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuoteProperties(VND, 0, RoundingMode.UNNECESSARY, Duration.ofHours(24), Duration.ofHours(24), 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuoteProperties(VND, 0, RoundingMode.HALF_UP, Duration.ofDays(8), Duration.ofHours(24), 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuoteProperties(VND, 0, RoundingMode.HALF_UP, Duration.ZERO, Duration.ofHours(24), 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuoteProperties(VND, 0, RoundingMode.HALF_UP, Duration.ofHours(24), Duration.ZERO, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuoteProperties(VND, 0, RoundingMode.HALF_UP, Duration.ofHours(24),
                Duration.ofHours(24), 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
