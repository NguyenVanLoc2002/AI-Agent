package com.secureerp.agent.policy.internal;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A synthetic sentinel (DATA_CLASSIFICATION §4). DIGITS tokens are matched against every numeric
 * projection; TEXT tokens against the look-alike skeleton of the text (SEC03-F1,
 * {@link TextNormalizer#skeleton}): at least 6 letters or digits once folded.
 */
public record Honeytoken(String id, Kind kind, String value) {

    private static final Pattern ID = Pattern.compile("^HT-[A-Z0-9-]{2,24}$");

    public enum Kind {
        DIGITS,
        TEXT
    }

    public Honeytoken {
        Objects.requireNonNull(kind, "kind");
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("invalid honeytoken id");
        }
        if (kind == Kind.DIGITS && (value == null || !value.matches("\\d{6,}"))) {
            throw new IllegalArgumentException("digit honeytoken needs at least 6 digits");
        }
        if (kind == Kind.TEXT && (value == null || TextNormalizer.skeleton(value).length() < 6)) {
            throw new IllegalArgumentException("text honeytoken needs at least 6 letters or digits");
        }
        value = kind == Kind.TEXT ? value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "") : value;
    }

    /**
     * PoC registry: synthetic values only (SECURITY_TEST_MATRIX SEC-01, DATA_CLASSIFICATION §4). A
     * non-PoC deployment would load its sentinels from a secret store instead.
     */
    public static List<Honeytoken> syntheticRegistry() {
        return List.of(
                new Honeytoken("HT-COST-01", Kind.DIGITS, "987654321"),
                new Honeytoken("HT-CREDIT-01", Kind.DIGITS, "123456789"),
                new Honeytoken("HT-PRICE-A", Kind.DIGITS, "555444333"),
                new Honeytoken("HT-PRICE-B", Kind.DIGITS, "666777888"),
                new Honeytoken("HT-PHONE-A", Kind.DIGITS, "0399000111"),
                new Honeytoken("HT-NAME-A", Kind.TEXT, "ZZHoney CustomerA"),
                new Honeytoken("HT-EMAIL-A", Kind.TEXT, "honey.customer.a@example.invalid"),
                new Honeytoken("HT-KEY-01", Kind.TEXT, "apasht_key_"));
    }
}
