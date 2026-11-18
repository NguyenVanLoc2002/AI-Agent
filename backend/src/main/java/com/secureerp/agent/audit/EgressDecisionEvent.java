package com.secureerp.agent.audit;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One egress decision, published by the gateway before any byte is sent (AI_GATEWAY §2.5, ADR-012
 * ACCEPTED 2026-11-18). Metadata only: trace ID, decision, policy code, honeytoken ID, structural
 * field names/path and byte length. No values and no per-value hash or HMAC (review F9).
 *
 * <p>Every string is validated against a narrow format, so a value cannot be smuggled into the
 * audit table by mistake. Defined in {@code audit} so that audit depends on no business module
 * (ARCH-10).
 */
public record EgressDecisionEvent(
        String traceId,
        Decision decision,
        String policyCode,
        String honeytokenId,
        String blockedFieldPath,
        List<String> fieldNames,
        int byteLength,
        Instant occurredAt) {

    public enum Decision {
        PASS,
        BLOCK
    }

    private static final Pattern TRACE_ID =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z_]{1,39}$");
    private static final Pattern HONEYTOKEN_ID = Pattern.compile("^HT-[A-Z0-9-]{2,24}$");
    private static final Pattern FIELD_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");
    private static final Pattern FIELD_PATH = Pattern.compile("^\\$(?:\\.[A-Za-z][A-Za-z0-9_]{0,63}|\\[\\d{1,4}\\]){0,16}$");
    private static final int MAX_FIELD_NAMES = 64;

    public EgressDecisionEvent {
        require(traceId, TRACE_ID, "traceId");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(occurredAt, "occurredAt");
        fieldNames = List.copyOf(fieldNames);
        if (fieldNames.size() > MAX_FIELD_NAMES) {
            throw new IllegalArgumentException("too many field names");
        }
        fieldNames.forEach(name -> require(name, FIELD_NAME, "fieldNames"));
        if (byteLength < 0) {
            throw new IllegalArgumentException("byteLength");
        }
        if (decision == Decision.PASS) {
            if (policyCode != null || honeytokenId != null || blockedFieldPath != null) {
                throw new IllegalArgumentException("a pass has no finding");
            }
        } else {
            require(policyCode, CODE, "policyCode");
            require(blockedFieldPath, FIELD_PATH, "blockedFieldPath");
            if (honeytokenId != null) {
                require(honeytokenId, HONEYTOKEN_ID, "honeytokenId");
            }
        }
    }

    private static void require(String value, Pattern pattern, String field) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid audit field: " + field);
        }
    }
}
