package com.secureerp.agent.policy;

import java.util.Objects;
import java.util.Set;

/**
 * Sanitizes text a customer typed before it can become a {@code SafeMessage} (DATA_CLASSIFICATION
 * §5, A-10 and A-11 APPROVED 2026-11-18).
 *
 * <ul>
 *   <li>money, phone numbers, email addresses: redacted to a placeholder and flagged;</li>
 *   <li>a full VIN: reduced to its WMI plus 14 asterisks;</li>
 *   <li>a honeytoken, or anything still detected after redaction: blocked (caller hands off).</li>
 * </ul>
 * Regex redaction does not anonymise text (BRD §08); the final-bytes scan still runs at the gateway.
 */
public interface InboundSanitizer {

    Result sanitize(String customerText);

    sealed interface Result permits Sanitized, Blocked {
    }

    record Sanitized(String text, Set<RedactionFlag> flags) implements Result {

        public Sanitized {
            Objects.requireNonNull(text, "text");
            flags = Set.copyOf(flags);
        }
    }

    record Blocked(PolicyCode policyCode, String honeytokenId) implements Result {

        public Blocked {
            Objects.requireNonNull(policyCode, "policyCode");
        }
    }

    enum RedactionFlag {
        MONEY,
        PHONE,
        EMAIL,
        VIN_MASKED
    }
}
