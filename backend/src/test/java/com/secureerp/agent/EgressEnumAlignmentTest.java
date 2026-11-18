package com.secureerp.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.gateway.api.AvailabilityBucket;
import com.secureerp.agent.gateway.api.Provenance;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Second review L3: {@code catalog.spi} and {@code gateway.api} each declare {@code Provenance} and
 * {@code AvailabilityBucket} (the egress types may not depend on catalog). The value sets must stay
 * identical, so a mapping by name (EP-03) can never fail or fall back to a default.
 *
 * <p>The catalog types are written fully qualified on purpose: they share the simple names of the
 * imported gateway types (the one exception in rule 20 "imports, not fully qualified names").
 */
class EgressEnumAlignmentTest {

    @Test
    void provenanceValuesMatch() {
        assertThat(names(Provenance.values()))
                .containsExactly(names(com.secureerp.agent.catalog.spi.Provenance.values()));
    }

    @Test
    void availabilityBucketValuesMatch() {
        assertThat(names(AvailabilityBucket.values()))
                .containsExactly(names(com.secureerp.agent.catalog.spi.AvailabilityBucket.values()));
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }
}
