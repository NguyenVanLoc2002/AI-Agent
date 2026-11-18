package com.secureerp.agent.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;
import java.util.Objects;

/**
 * One conversation segment sent to the model (AI_GATEWAY §1, review F3/F4).
 *
 * <p>There is deliberately no role for staff messages or free-text tool output. CUSTOMER text must
 * come from the inbound sanitizer; CONTEXT_SUMMARY text from the deterministic history summary
 * (ADR-011, later issue).
 */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record SafeMessage(Role role, String text) {

    public static final int MAX_TEXT_CHARS = 2000;

    public enum Role {
        CUSTOMER,
        CONTEXT_SUMMARY
    }

    public SafeMessage {
        Objects.requireNonNull(role, "role");
        EgressText.text(text, "text", MAX_TEXT_CHARS);
    }
}
