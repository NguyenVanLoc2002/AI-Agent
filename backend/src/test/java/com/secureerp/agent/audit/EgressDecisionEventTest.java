package com.secureerp.agent.audit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The audit event accepts metadata shapes only, so a value cannot reach egress_record by mistake. */
class EgressDecisionEventTest {

    private static final String TRACE = "0b6c3a52-4f7e-4c1d-9a8b-2e5f6d7c8b9a";
    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");

    @Test
    void passAndBlockShapesAreAccepted() {
        assertThatCode(() -> new EgressDecisionEvent(TRACE, EgressDecisionEvent.Decision.PASS, null, null, null,
                List.of("messages", "text"), 120, NOW)).doesNotThrowAnyException();
        assertThatCode(() -> new EgressDecisionEvent(TRACE, EgressDecisionEvent.Decision.BLOCK, "HONEYTOKEN",
                "HT-COST-01", "$.messages[0].text", List.of("messages"), 120, NOW)).doesNotThrowAnyException();
    }

    @Test
    void valuesCannotBeSmuggledIntoMetadataFields() {
        assertThatThrownBy(() -> new EgressDecisionEvent("987654321", EgressDecisionEvent.Decision.PASS, null, null,
                null, List.of(), 0, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EgressDecisionEvent(TRACE, EgressDecisionEvent.Decision.BLOCK, "HONEYTOKEN",
                null, "$.messages[0].text=987654321", List.of(), 0, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EgressDecisionEvent(TRACE, EgressDecisionEvent.Decision.BLOCK, "cost 987654321",
                null, "$", List.of(), 0, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EgressDecisionEvent(TRACE, EgressDecisionEvent.Decision.PASS, null, null, null,
                List.of("gia von 987654321"), 0, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EgressDecisionEvent(TRACE, EgressDecisionEvent.Decision.BLOCK, "HONEYTOKEN",
                "987654321", "$", List.of(), 0, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void passCannotCarryAFinding() {
        assertThatThrownBy(() -> new EgressDecisionEvent(TRACE, EgressDecisionEvent.Decision.PASS, "MONEY", null,
                null, List.of(), 0, NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
