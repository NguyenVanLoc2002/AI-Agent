package com.secureerp.agent.quote.internal;

import com.secureerp.agent.platform.Money;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Currency;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Quote settings (owner decisions 2026-11-18): ADR-014 money rules and the ADR-008 quote validity.
 *
 * @param currency   quote currency (VND)
 * @param moneyScale decimal places of line totals and the total (VND: 0)
 * @param rounding   rounding of each line total (HALF_UP)
 * @param validity   how long an issued quote and its handle are valid (24 h; at most 7 days)
 * @param lockAfterNotQuoted              no automatic quote in a conversation for this long after a
 *                                        business refusal, for the customer (A-34 APPROVED 2026-11-19: 24 h)
 * @param maxIssuedPerConversationPerHour ISSUED quotes per customer per rolling hour, all conversations
 *                                        (A-34: 5; the property name predates the per-customer scope)
 */
@ConfigurationProperties("apas.quote")
record QuoteProperties(
        @DefaultValue("VND") Currency currency,
        @DefaultValue("0") int moneyScale,
        @DefaultValue("HALF_UP") RoundingMode rounding,
        @DefaultValue("PT24H") Duration validity,
        @DefaultValue("PT24H") Duration lockAfterNotQuoted,
        @DefaultValue("5") int maxIssuedPerConversationPerHour) {

    static final Duration MAX_VALIDITY = Duration.ofDays(7);

    QuoteProperties {
        if (currency == null || rounding == null || rounding == RoundingMode.UNNECESSARY) {
            throw new IllegalArgumentException("apas.quote currency and rounding are required");
        }
        if (moneyScale < 0 || moneyScale > Money.MAX_SCALE) {
            throw new IllegalArgumentException("apas.quote.money-scale must be 0.." + Money.MAX_SCALE);
        }
        if (validity == null || validity.isNegative() || validity.isZero() || validity.compareTo(MAX_VALIDITY) > 0) {
            throw new IllegalArgumentException("apas.quote.validity must be positive and at most 7 days");
        }
        if (lockAfterNotQuoted == null || lockAfterNotQuoted.isNegative() || lockAfterNotQuoted.isZero()) {
            throw new IllegalArgumentException("apas.quote.lock-after-not-quoted must be positive");
        }
        if (maxIssuedPerConversationPerHour < 1) {
            throw new IllegalArgumentException("apas.quote.max-issued-per-conversation-per-hour must be positive");
        }
    }
}
