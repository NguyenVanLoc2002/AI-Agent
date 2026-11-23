package com.secureerp.agent.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** SEC01-FLAKE-UUID: the sentinel checks ignore the random turnId and nothing else. */
class ChatBodiesTest {

    private static final String TURN = "\"turnId\":\"ef481555-7d73-4d42-9cbb-8c7f93afd401\"";

    @Test
    void aSentinelInsideTheTurnIdIsNotMatched() {
        String body = "{" + TURN + ",\"reply\":\"Yêu cầu đã được chuyển\",\"handedOff\":true,\"quoteCard\":null}";

        assertThat(ChatBodies.withoutTurnId(body)).doesNotContain("555").doesNotContain("401")
                .contains("\"turnId\":\"-\"").contains("\"handedOff\":true");
    }

    @Test
    void aSentinelAnywhereElseIsStillMatched() {
        assertThat(ChatBodies.withoutTurnId("{" + TURN + ",\"reply\":\"Giá 555.444.333 đ\"}")).contains("555");
        assertThat(ChatBodies.withoutTurnId("{" + TURN + ",\"reply\":\"x\",\"quoteCard\":{\"total\":\"401\"}}"))
                .contains("401");
    }

    @Test
    void aBodyWithoutOrWithSeveralTurnIdsIsRejected() {
        assertThatThrownBy(() -> ChatBodies.withoutTurnId("{\"reply\":\"555\"}")).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> ChatBodies.withoutTurnId("{" + TURN + "," + TURN + "}"))
                .isInstanceOf(AssertionError.class);
        // Not in UUID form: kept, so a sentinel placed there is still matched.
        assertThatThrownBy(() -> ChatBodies.withoutTurnId("{\"turnId\":\"555\"}")).isInstanceOf(AssertionError.class);
    }
}
