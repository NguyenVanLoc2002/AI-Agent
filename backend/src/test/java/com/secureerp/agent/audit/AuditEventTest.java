package com.secureerp.agent.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.identity.Role;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** EP-09b: an audit event takes codes and a pseudonym only; free text cannot be smuggled in (TH-15). */
class AuditEventTest {

    private static final AuditActor ACTOR = new AuditActor("usr_AuditTestActorAAAAAAAA", "SALES");

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "x", "READ ", "read", "0K", "Báo giá", "987654321",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "BG-ABCDEFGH", "cv_AAAAAAAAAAAAAAAAAAAAAA"})
    void anOutcomeIsACode(String outcome) {
        assertThatThrownBy(() -> new AuditEvent(AuditEvent.Action.HANDOFF_CLAIMED, AuditEvent.Resource.HANDOFF,
                outcome, ACTOR)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "sales-1", "usr_short", "usr_AuditTestActorAAAAAAAA0", "cref_AAAAAAAAAAAAAAAAAAAAAA",
            "usr_has spaces in it AAAA"})
    void anActorIsAPseudonym(String pseudonym) {
        assertThatThrownBy(() -> new AuditActor(pseudonym, "SALES")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "ADMIN", "sales", "AGENT"})
    void aRoleIsAnRbacRole(String role) {
        assertThatThrownBy(() -> new AuditActor("usr_AuditTestActorAAAAAAAA", role))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rolesMatchTheIdentityRoles() {
        assertThat(AuditActor.ROLES).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(Role.values()).map(Enum::name).toList());
    }

    @Test
    void aDenialCarriesTheCodeAndTheActor() {
        AuditEvent denied = AuditEvent.denied(AuditEvent.Resource.QUOTE, AuditEvent.FORBIDDEN_RESOURCE, ACTOR);

        assertThat(denied.action()).isEqualTo(AuditEvent.Action.ACCESS_DENIED);
        assertThat(denied.outcome()).isEqualTo("FORBIDDEN_RESOURCE");
        assertThat(ACTOR.toString()).doesNotContain("usr_");
    }
}
