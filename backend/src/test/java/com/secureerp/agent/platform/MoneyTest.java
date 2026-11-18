package com.secureerp.agent.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** ADR-014 (ACCEPTED 2026-11-18): BigDecimal, compareTo semantics, no amount in toString. */
class MoneyTest {

    private static final Currency VND = Currency.getInstance("VND");
    private static final Currency USD = Currency.getInstance("USD");

    private static Money vnd(String amount) {
        return Money.of(new BigDecimal(amount), VND);
    }

    @Test
    void equalityIgnoresScale() {
        assertThat(vnd("1000")).isEqualTo(vnd("1000.00")).hasSameHashCodeAs(vnd("1000.0000"));
        assertThat(vnd("1000").compareTo(vnd("999.9999"))).isPositive();
        assertThat(vnd("1000")).isNotEqualTo(Money.of(new BigDecimal("1000"), USD));
    }

    @ParameterizedTest
    @CsvSource({
        "555444333.5, 1, 0, HALF_UP, 555444334",
        "555444333.5, 1, 0, HALF_EVEN, 555444334",
        "555444332.5, 1, 0, HALF_EVEN, 555444332",
        "555444332.5, 1, 0, HALF_UP, 555444333",
        "0.3333, 3, 2, HALF_UP, 1.00",
        "1999.995, 1, 2, HALF_UP, 2000.00",
        "1999.995, 1, 2, DOWN, 1999.99"
    })
    void timesAndRoundedFollowTheConfiguredRule(String unit, int quantity, int scale, RoundingMode rounding,
            String expected) {
        Money result = vnd(unit).times(quantity).rounded(scale, rounding);

        assertThat(result.amount()).isEqualByComparingTo(expected);
        assertThat(result.amount().scale()).isEqualTo(scale);
    }

    @Test
    void invalidAmountsAreRejected() {
        assertThatThrownBy(() -> vnd("-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> vnd("1.00001")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> vnd("1000000000000000")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> vnd("1").times(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> vnd("1").rounded(5, RoundingMode.HALF_UP)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void currenciesNeverMix() {
        assertThatThrownBy(() -> vnd("1").plus(Money.of(BigDecimal.ONE, USD))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> vnd("1").compareTo(Money.of(BigDecimal.ONE, USD)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Rule 10: an amount cannot reach a log line through string concatenation. */
    @Test
    void toStringNeverPrintsTheAmount() {
        assertThat(vnd("555444333").toString()).doesNotContain("555444333").contains("VND");
    }
}
