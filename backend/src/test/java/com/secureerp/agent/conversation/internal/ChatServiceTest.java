package com.secureerp.agent.conversation.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.secureerp.agent.agent.Agent;
import com.secureerp.agent.agent.AgentReply;
import com.secureerp.agent.catalog.VehicleContext;
import com.secureerp.agent.catalog.VinIntake;
import com.secureerp.agent.conversation.ChatTurn;
import com.secureerp.agent.handoff.HandoffReason;
import com.secureerp.agent.handoff.Handoffs;
import com.secureerp.agent.identity.IdentityFixtures;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.policy.InboundSanitizer;
import com.secureerp.agent.quote.QuoteCards;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Failure paths after a message is admitted (EP-04 reviews: security M2, architecture M2/M3): every admitted
 * turn ends with a stored reply and the lock plus handoff record are written together.
 */
class ChatServiceTest {

    private static final ConversationId ID = new ConversationId("cv_ChatServiceTestAAAAAAA");

    private final ConversationStore store = mock(ConversationStore.class);
    private final InboundSanitizer sanitizer = mock(InboundSanitizer.class);
    private final VehicleContext vehicles = mock(VehicleContext.class);
    private final Agent agent = mock(Agent.class);
    private final QuoteCards cards = mock(QuoteCards.class);
    private final Handoffs handoffs = mock(Handoffs.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final SessionIdentity identity = IdentityFixtures.sessionIdentity("ChatSvc", "ChatSvcCustomer");
    private ChatService chat;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        chat = new ChatService(store, sanitizer, vehicles, agent, cards, handoffs,
                new ConversationProperties(5, 30, 60, 8192, Duration.ofMinutes(2)), transactions,
                Clock.fixed(Instant.parse("2026-11-19T08:00:00Z"), ZoneOffset.UTC));
        doAnswer(invocation -> {
            ((Consumer<TransactionStatus>) invocation.getArgument(0)).accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());
        String digest = identity.customer().orElseThrow().bindingDigest();
        when(store.findOwned(identity.principal(), ID))
                .thenReturn(Optional.of(new ConversationStore.Conversation(ID, digest, ConversationStore.Status.ACTIVE_BOT)));
        when(sanitizer.sanitize(anyString())).thenReturn(new InboundSanitizer.Sanitized("Xin chào", Set.of()));
        when(store.admit(any(), any(), any(), any(), anyString(), anyBoolean(), any()))
                .thenReturn(new ConversationStore.Admission.Admitted(7L, ConversationStore.Status.ACTIVE_BOT));
        when(vehicles.register(any(), anyString())).thenReturn(new VinIntake.None());
        when(store.customerTextsUpTo(eq(ID), anyLong(), anyInt())).thenReturn(List.of("Xin chào"));
    }

    /** Owner decision 2026-11-19 (A-42(e)): a linked customer no team serves cannot start a conversation. */
    @Test
    void aCustomerWithoutATeamCannotStartAConversation() {
        SessionIdentity unassigned = IdentityFixtures.unassignedCustomer("NoTeam", "NoTeamCustomer");

        assertThatThrownBy(() -> chat.create(unassigned)).isInstanceOfSatisfying(ApplicationException.class,
                refused -> assertThat(refused.code()).isEqualTo(ErrorCode.CUSTOMER_NOT_ASSIGNED));

        verify(store, never()).create(any(), anyString(), anyString(), any(), anyInt(), any());
    }

    /**
     * The mocked store knows no team for the conversation (as one created before teams existed), so the
     * handoff takes the team serving the session customer (review round 2, 2026-11-19).
     */
    @Test
    void anAgentFailureEndsAsAStoredHandoff() {
        when(agent.reply(any())).thenThrow(new IllegalStateException("boom 987654321"));
        ChatTurn turn = chat.post(identity, ID, UUID.randomUUID(), "Xin chào");
        assertThat(turn.handedOff()).isTrue();
        assertThat(turn.reply()).isEqualTo(ChatTexts.HANDED_OFF);
        verify(store).markHandoffPending(identity.principal(), ID);
        verify(handoffs).open(ID, HandoffReason.PROVIDER_ERROR, IdentityFixtures.TEAM_1);
        verify(store).insertBot(eq(ID), any(), eq(ChatTexts.HANDED_OFF), eq(true));
    }

    @Test
    void aCardThatDoesNotRenderHandsOffInsteadOfAnsweringAboutIt() {
        when(agent.reply(any())).thenReturn(new AgentReply.Answer("Báo giá đã được gửi.", "qh_QQQQQQQQQQQQQQQQQQQQQQ"));
        when(cards.render(anyString(), any(), any())).thenReturn(Optional.empty());
        ChatTurn turn = chat.post(identity, ID, UUID.randomUUID(), "báo giá");
        assertThat(turn.handedOff()).isTrue();
        verify(handoffs).open(ID, HandoffReason.QUOTE_NOT_ISSUED, IdentityFixtures.TEAM_1);
        verify(store, never()).insertBot(eq(ID), any(), eq("Báo giá đã được gửi."), anyBoolean());
    }

    @Test
    void aRenderFailureEndsAsAStoredHandoff() {
        when(agent.reply(any())).thenReturn(new AgentReply.Answer("Báo giá đã được gửi.", "qh_QQQQQQQQQQQQQQQQQQQQQQ"));
        when(cards.render(anyString(), any(), any())).thenThrow(new IllegalStateException("db down"));
        ChatTurn turn = chat.post(identity, ID, UUID.randomUUID(), "báo giá");
        assertThat(turn.handedOff()).isTrue();
        verify(handoffs).open(ID, HandoffReason.PROVIDER_ERROR, IdentityFixtures.TEAM_1);
    }

    @Test
    void theHistoryIsBoundedByThisTurnsMessage() {
        when(agent.reply(any())).thenReturn(new AgentReply.Answer("Chào bạn", null));
        chat.post(identity, ID, UUID.randomUUID(), "Xin chào");
        verify(store).customerTextsUpTo(ID, 7L, 6);
    }
}
