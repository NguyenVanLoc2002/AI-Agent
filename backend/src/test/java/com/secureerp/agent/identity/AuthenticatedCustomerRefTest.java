package com.secureerp.agent.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** ARCH-08 support: the reference is opaque, redacted and not publicly constructible. */
class AuthenticatedCustomerRefTest {

    @Test
    void hasNoPublicConstructorOrFactory() {
        assertThat(AuthenticatedCustomerRef.class.getConstructors()).isEmpty();
        assertThat(Arrays.stream(AuthenticatedCustomerRef.class.getDeclaredMethods())
                .filter(m -> Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers())))
                .isEmpty();
    }

    @Test
    void toStringIsRedacted() {
        AuthenticatedCustomerRef ref = AuthenticatedCustomerRef.fromVerifiedBinding("cref_synthCustomerA0001");
        assertThat(ref.toString()).doesNotContain("synthCustomerA0001").contains("redacted");
    }

    @Test
    void equalityUsesTheOpaqueValue() {
        assertThat(AuthenticatedCustomerRef.fromVerifiedBinding("cref_synthCustomerA0001"))
                .isEqualTo(AuthenticatedCustomerRef.fromVerifiedBinding("cref_synthCustomerA0001"))
                .isNotEqualTo(AuthenticatedCustomerRef.fromVerifiedBinding("cref_synthCustomerB0001"));
    }

    /** Architecture review M4(d): a client JSON value must never become a customer reference. */
    @Test
    void cannotBeDeserializedFromClientJson() {
        JsonMapper mapper = JsonMapper.builder().build();
        assertThatThrownBy(() -> mapper.readValue("\"cref_synthCustomerA0001\"", AuthenticatedCustomerRef.class))
                .isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"opaqueValue\":\"cref_synthCustomerA0001\"}",
                AuthenticatedCustomerRef.class)).isInstanceOf(JacksonException.class);
    }

    @Test
    void rejectsMalformedValuesWithoutEchoingThem() {
        assertThatThrownBy(() -> AuthenticatedCustomerRef.fromVerifiedBinding("12345"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("12345");
    }
}
