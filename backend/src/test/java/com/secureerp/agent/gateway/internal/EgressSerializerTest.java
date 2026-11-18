package com.secureerp.agent.gateway.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.gateway.EgressFixtures;
import com.secureerp.agent.gateway.api.ModelRequest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** T-SEC01-SCHEMA (serialization part): the bytes contain only allowlisted property names. */
class EgressSerializerTest {

    @Test
    void serializedRequestUsesOnlyAllowlistedNamesAndFixedDiscriminators() {
        ModelRequest request = EgressFixtures.request("Toi can ma phanh");
        byte[] body = EgressSerializer.serialize(request);
        String json = new String(body, StandardCharsets.UTF_8);

        EgressSerializer.Structure structure = EgressSerializer.structure(body).orElseThrow();
        assertThat(structure.fieldNames()).isSubsetOf(EgressSerializer.ALLOWED_PROPERTY_NAMES);
        assertThat(json).contains("\"type\":\"productSearch\"", "\"type\":\"quoteRequest\"", "\"type\":\"handoff\"")
                .doesNotContain("com.secureerp").doesNotContain("@class").doesNotContain("null");
        assertThat(structure.payload().segments()).extracting(s -> s.fieldPath())
                .contains("$.messages[0].text", "$.context.verifiedProducts[0].sku");
    }

    @Test
    void allowlistIsDerivedFromTheEgressRecordsOnly() {
        assertThat(EgressSerializer.ALLOWED_PROPERTY_NAMES)
                .contains("traceId", "messages", "role", "text", "candidates", "quoteHandle", "lineCount", "type")
                .doesNotContain("unitPrice", "customerId", "purchaseCost", "quantityOnHand", "@class");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "{\"traceId\":\"x\",\"unitPrice\":1}",
        "{\"messages\":[{\"text\":\"a\",\"metadata\":{\"cost\":1}}]}",
        "{\"traceId\":\"x\",\"traceId\":\"y\"}",
        "[{\"text\":\"a\"}]",
        "not json"
    })
    void unclassifiedOrMalformedStructureIsRejected(String json) {
        assertThat(EgressSerializer.structure(json.getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    void deeplyNestedStructureIsRejected() {
        String nested = "{\"messages\":" + "[".repeat(20) + "\"a\"" + "]".repeat(20) + "}";
        assertThat(EgressSerializer.structure(nested.getBytes(StandardCharsets.UTF_8))).isEmpty();
    }
}
