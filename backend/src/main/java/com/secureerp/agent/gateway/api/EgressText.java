package com.secureerp.agent.gateway.api;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validation helpers shared by the egress records (AI_GATEWAY §1). Static only: it has no instance
 * fields, so ARCH-05/06 do not apply to it.
 */
public final class EgressText {

    /** C0/C1 controls except tab/newline, bidi overrides and isolates (obfuscation / spoofing). */
    private static final Pattern FORBIDDEN_CHARS =
            Pattern.compile("[\\x00-\\x08\\x0B-\\x1F\\x7F-\\x9F\\u202A-\\u202E\\u2066-\\u2069]");

    /**
     * Category label from catalog data: letters and single spaces only, no digits or punctuation.
     * ERP-controlled text inside a tool result would otherwise be a free-text channel past the type
     * gate (security review M2). DESIGN PROPOSAL until a closed category taxonomy exists.
     */
    static final Pattern CATEGORY_LABEL = Pattern.compile("^\\p{L}+(?: \\p{L}+){0,15}$");

    /** Seven or more digits in a row: phone numbers, money, honeytokens, VIN serials. */
    private static final Pattern DIGIT_RUN = Pattern.compile("\\d{7,}");

    /** Length of the random part of a server token: 22 base64url characters = 128 bits (ADR-008). */
    public static final int SERVER_TOKEN_CHARS = 22;

    private EgressText() {
    }

    /**
     * Opaque server-issued token ({@code productRef}, {@code vinRef}, {@code quoteHandle}): the
     * prefix plus exactly {@value #SERVER_TOKEN_CHARS} base64url characters, with no run of 7+
     * digits. The egress policy gives these fields the honeytoken check only, so the format must
     * leave no room for a business value (second review M4): {@code "pr_" + sku} or
     * {@code "vin_" + fullVin} do not fit. Issuers must generate random tokens (and draw again in
     * the rare case of a digit run). The format cannot prove where a token came from; see A-26.
     */
    public static String serverToken(String value, String field, String prefix) {
        Objects.requireNonNull(value, field);
        if (!value.startsWith(prefix) || value.length() != prefix.length() + SERVER_TOKEN_CHARS
                || !value.substring(prefix.length()).chars().allMatch(EgressText::isBase64Url)
                || DIGIT_RUN.matcher(value).find()) {
            throw invalid(field);
        }
        return value;
    }

    public static String optionalServerToken(String value, String field, String prefix) {
        return value == null ? null : serverToken(value, field, prefix);
    }

    /** Server-chosen identifier (e.g. a prompt template ID): format plus no run of 7+ digits. */
    public static String serverIdentifier(String value, String field, Pattern pattern) {
        matching(value, field, pattern);
        if (DIGIT_RUN.matcher(value).find()) {
            throw invalid(field);
        }
        return value;
    }

    private static boolean isBase64Url(int c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
    }

    public static String categoryLabel(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.length() > 80 || !CATEGORY_LABEL.matcher(value).matches()) {
            throw invalid(field);
        }
        return value;
    }

    /** Free text (already sanitized upstream; the final-bytes scan still runs). */
    public static String text(String value, String field, int maxChars) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maxChars) {
            throw invalid(field);
        }
        if (FORBIDDEN_CHARS.matcher(value).find()) {
            throw invalid(field);
        }
        return value;
    }

    public static String optionalText(String value, String field, int maxChars) {
        return value == null ? null : text(value, field, maxChars);
    }

    public static String matching(String value, String field, Pattern pattern) {
        Objects.requireNonNull(value, field);
        if (!pattern.matcher(value).matches()) {
            throw invalid(field);
        }
        return value;
    }

    public static String optionalMatching(String value, String field, Pattern pattern) {
        return value == null ? null : matching(value, field, pattern);
    }

    /** Null becomes an empty list; the copy is immutable and rejects null elements. */
    public static <T> List<T> list(List<T> values, String field, int maxSize) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > maxSize) {
            throw invalid(field);
        }
        return List.copyOf(values);
    }

    /** The message names only the field, never the rejected value. */
    static IllegalArgumentException invalid(String field) {
        return new IllegalArgumentException("invalid egress field: " + field);
    }
}
