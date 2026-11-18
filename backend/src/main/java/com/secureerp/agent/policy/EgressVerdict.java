package com.secureerp.agent.policy;

import java.util.Objects;

/**
 * Result of the final-bytes scan. A block carries only metadata: policy code, honeytoken ID (for a
 * sentinel hit) and the structural field path. Never the matched value (AI_GATEWAY §2.4–2.5).
 */
public record EgressVerdict(boolean allowed, PolicyCode policyCode, String honeytokenId, String fieldPath) {

    private static final EgressVerdict PASS = new EgressVerdict(true, null, null, null);

    public EgressVerdict {
        if (allowed && (policyCode != null || honeytokenId != null || fieldPath != null)) {
            throw new IllegalArgumentException("a pass carries no finding");
        }
        if (!allowed) {
            Objects.requireNonNull(policyCode, "policyCode");
            Objects.requireNonNull(fieldPath, "fieldPath");
        }
    }

    public static EgressVerdict pass() {
        return PASS;
    }

    public static EgressVerdict block(PolicyCode code, String honeytokenId, String fieldPath) {
        return new EgressVerdict(false, code, honeytokenId, fieldPath);
    }
}
