package com.secureerp.agent.conversation.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.conversation.Chat.CustomerState;
import com.secureerp.agent.conversation.internal.ConversationStore.Status;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** UX13-CONV-STATE (A-62): one customer-visible state per stored status, and no internal name leaks. */
class CustomerStateTest {

    @ParameterizedTest
    @CsvSource({"ACTIVE_BOT, BOT", "HANDOFF_PENDING, WAITING_STAFF", "HUMAN_ACTIVE, STAFF"})
    void eachStatusHasItsCustomerState(Status status, CustomerState expected) {
        assertThat(ChatService.customerState(status)).isEqualTo(expected);
    }

    @Test
    void everyStatusIsMappedAndNoStateNamesAStatus() {
        assertThat(Arrays.stream(Status.values()).map(ChatService::customerState))
                .containsExactlyInAnyOrder(CustomerState.values());
        assertThat(Arrays.stream(CustomerState.values()).map(Enum::name))
                .doesNotContainAnyElementsOf(Arrays.stream(Status.values()).map(Enum::name).toList());
    }
}
