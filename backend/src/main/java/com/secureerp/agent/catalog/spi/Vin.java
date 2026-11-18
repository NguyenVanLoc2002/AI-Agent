package com.secureerp.agent.catalog.spi;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A validated vehicle identification number (BR-04, UC-02 AC-02.1). Context-identifying (BR-30,
 * DATA_CLASSIFICATION §2): it stays server-side; the model sees only {@link #masked()}.
 *
 * <p>Rules:
 * <ul>
 *   <li>exactly 17 characters from {@code A-Z} and {@code 0-9} without {@code I}, {@code O},
 *       {@code Q} (SOURCE FACT, BRD §04); input is upper-cased first;</li>
 *   <li>check digit (position 9, ISO 3779 weights) required only when the first character is
 *       {@code 1}–{@code 5} (North America, 49 CFR 565). Other regions: format only. DESIGN
 *       PROPOSAL, approved by the owner for EP-05 on 2026-11-18 (BR-04 "when applicable").</li>
 * </ul>
 * {@link #toString()} is masked so a VIN cannot reach a log line through string concatenation.
 */
public record Vin(String value) {

    public static final int LENGTH = 17;
    private static final Pattern FORMAT = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");
    private static final int[] WEIGHTS = {8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int CHECK_DIGIT_INDEX = 8;
    private static final String MASK = "*".repeat(LENGTH - 3);

    public Vin {
        Objects.requireNonNull(value, "value");
        value = value.strip().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid vin format");
        }
        if (requiresCheckDigit(value) && value.charAt(CHECK_DIGIT_INDEX) != checkDigit(value)) {
            throw new IllegalArgumentException("invalid vin check digit");
        }
    }

    /** True if {@code text} is a valid VIN under the rules above. */
    public static boolean isValid(String text) {
        try {
            new Vin(text);
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    /** World manufacturer identifier (first three characters). */
    public String wmi() {
        return value.substring(0, 3);
    }

    /** WMI followed by 14 asterisks: the only form allowed at egress (A-11, {@code TechnicalContext.maskedVin}). */
    public String masked() {
        return wmi() + MASK;
    }

    @Override
    public String toString() {
        return "Vin[" + masked() + "]";
    }

    static boolean requiresCheckDigit(String vin) {
        char region = vin.charAt(0);
        return region >= '1' && region <= '5';
    }

    /** ISO 3779 / 49 CFR 565.15 check digit: weighted sum of transliterated values modulo 11, 10 = X. */
    static char checkDigit(String vin) {
        int sum = 0;
        for (int i = 0; i < LENGTH; i++) {
            sum += transliterate(vin.charAt(i)) * WEIGHTS[i];
        }
        int remainder = sum % 11;
        return remainder == 10 ? 'X' : (char) ('0' + remainder);
    }

    private static int transliterate(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        return switch (c) {
            case 'A', 'J' -> 1;
            case 'B', 'K', 'S' -> 2;
            case 'C', 'L', 'T' -> 3;
            case 'D', 'M', 'U' -> 4;
            case 'E', 'N', 'V' -> 5;
            case 'F', 'W' -> 6;
            case 'G', 'P', 'X' -> 7;
            case 'H', 'Y' -> 8;
            case 'R', 'Z' -> 9;
            default -> throw new IllegalArgumentException("invalid vin character");
        };
    }
}
