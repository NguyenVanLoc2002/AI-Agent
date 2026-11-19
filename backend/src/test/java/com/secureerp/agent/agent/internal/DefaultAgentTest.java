package com.secureerp.agent.agent.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.agent.AgentReply;
import com.secureerp.agent.agent.AgentTurn;
import com.secureerp.agent.gateway.AiGateway;
import com.secureerp.agent.gateway.GatewayResult;
import com.secureerp.agent.gateway.api.AvailabilityBucket;
import com.secureerp.agent.gateway.api.HandoffResult;
import com.secureerp.agent.gateway.api.ModelRequest;
import com.secureerp.agent.gateway.api.ModelResponse;
import com.secureerp.agent.gateway.api.ProductRef;
import com.secureerp.agent.gateway.api.ProductSearchResult;
import com.secureerp.agent.gateway.api.Provenance;
import com.secureerp.agent.gateway.api.QuoteRequestResult;
import com.secureerp.agent.gateway.api.SafeMessage;
import com.secureerp.agent.gateway.api.ToolErrorResult;
import com.secureerp.agent.gateway.api.ToolProposal;
import com.secureerp.agent.gateway.api.ToolResultDto;
import com.secureerp.agent.gateway.api.ToolSpec;
import com.secureerp.agent.identity.Channel;
import com.secureerp.agent.identity.IdentityFixtures;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.tools.ToolCallContext;
import com.secureerp.agent.tools.ToolManager;
import com.secureerp.agent.tools.ToolOutcome;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** EP-04 agent loop: ADR-011 history, loop limit, handoff mapping, output validation (decisions 2026-11-19). */
class DefaultAgentTest {

    private static final ConversationId CONVERSATION = new ConversationId("cv_AgentTestAAAAAAAAAAAAA");
    private static final String TURN = "00000000-0000-4000-8000-000000000004";
    private static final String HANDLE = "qh_QQQQQQQQQQQQQQQQQQQQQQ";
    private static final ProductRef BRAKE_PAD = new ProductRef("pr_PPPPPPPPPPPPPPPPPPPPPP", "SKU-BRK-PAD-A",
            "04465-0K290", "Ma phanh truoc", Provenance.VERIFIED, AvailabilityBucket.CON_HANG);

    private final Deque<GatewayResult> answers = new ArrayDeque<>();
    private final List<ModelRequest> requests = new ArrayList<>();
    private final List<ToolProposal> executed = new ArrayList<>();
    private Function<ToolProposal, ToolOutcome> toolBehaviour = proposal ->
            new ToolOutcome(new ProductSearchResult(List.of(BRAKE_PAD)), false);

    private final AiGateway gateway = request -> {
        requests.add(request);
        return answers.isEmpty() ? text("Xin chào") : answers.removeFirst();
    };

    private final ToolManager tools = new ToolManager() {
        @Override
        public List<ToolSpec> toolsFor(Role role, Channel channel) {
            return List.of();
        }

        @Override
        public ToolOutcome execute(ToolCallContext context, ToolProposal proposal) {
            assertThat(context.conversation()).isEqualTo(CONVERSATION);
            assertThat(context.turnId()).isEqualTo(TURN);
            assertThat(context.customer()).isEqualTo(IdentityFixtures.customer("AgentCustomer"));
            executed.add(proposal);
            return toolBehaviour.apply(proposal);
        }
    };

    private final DefaultAgent agent = new DefaultAgent(gateway, tools);

    private static AgentTurn turn(String... texts) {
        return new AgentTurn(IdentityFixtures.principal("AgentPrincipal"), Role.CUST, Channel.WEB_CHAT, CONVERSATION,
                TURN, IdentityFixtures.customer("AgentCustomer"), List.of(texts), null);
    }

    private static GatewayResult text(String text) {
        return new GatewayResult.Completed(new ModelResponse(text, List.of()));
    }

    private static GatewayResult tool(String name, String arguments) {
        return new GatewayResult.Completed(new ModelResponse(null, List.of(new ToolProposal(name, arguments))));
    }

    @Test
    void historyIsCustomerOnlyAndDeterministic() {
        agent.reply(turn("Tôi cần má phanh", "Xe Vios 2019", "Mã OE 04465-0K290"));
        List<SafeMessage> messages = requests.getFirst().messages();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).role()).isEqualTo(SafeMessage.Role.CONTEXT_SUMMARY);
        assertThat(messages.get(0).text()).contains("- Tôi cần má phanh\n- Xe Vios 2019");
        assertThat(messages.get(1)).isEqualTo(new SafeMessage(SafeMessage.Role.CUSTOMER, "Mã OE 04465-0K290"));
        assertThat(requests.getFirst().toolResults()).isEmpty();
    }

    @Test
    void aSingleMessageHasNoSummary() {
        agent.reply(turn("Xin chào"));
        assertThat(requests.getFirst().messages())
                .containsExactly(new SafeMessage(SafeMessage.Role.CUSTOMER, "Xin chào"));
    }

    @Test
    void theSummaryKeepsTheNewestMessagesWithinTheSegmentLimit() {
        String long1 = "a".repeat(1500);
        String long2 = "b".repeat(1500);
        agent.reply(turn(long1, long2, "câu hỏi"));
        String summary = requests.getFirst().messages().getFirst().text();
        assertThat(summary).contains(long2).doesNotContain(long1);
        assertThat(summary.length()).isLessThanOrEqualTo(SafeMessage.MAX_TEXT_CHARS);
    }

    @Test
    void toolResultsGoBackToTheModelAndTheirCodesMayBeNamed() {
        answers.add(tool("searchVerifiedProduct", "{\"oe\":\"04465-0K290\"}"));
        answers.add(text("Có má phanh 04465-0K290 (SKU-BRK-PAD-A)."));
        AgentReply reply = agent.reply(turn("Mã OE 04465-0K290"));
        assertThat(reply).isEqualTo(new AgentReply.Answer("Có má phanh 04465-0K290 (SKU-BRK-PAD-A).", null));
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).toolResults()).containsExactly(new ProductSearchResult(List.of(BRAKE_PAD)));
    }

    @Test
    void anInventedPartCodeIsDroppedAndHandedOff() {
        answers.add(tool("searchVerifiedProduct", "{\"oe\":\"04465-0K290\"}"));
        answers.add(text("Bạn nên dùng mã 04465-0K999."));
        assertThat(agent.reply(turn("x"))).isEqualTo(new AgentReply.Handoff(AgentReply.Reason.OUTPUT_REJECTED));
    }

    @Test
    void aPriceInTheAnswerIsDroppedAndHandedOff() {
        answers.add(text("Giá là 555.444.333 đ"));
        assertThat(agent.reply(turn("x"))).isEqualTo(new AgentReply.Handoff(AgentReply.Reason.OUTPUT_REJECTED));
    }

    @Test
    void anIssuedQuoteHandleComesFromTheToolResultOnly() {
        toolBehaviour = proposal -> new ToolOutcome(new QuoteRequestResult(QuoteRequestResult.Status.ISSUED, HANDLE, 1),
                false);
        answers.add(tool("requestQuote", "{\"lines\":[{\"productRef\":\"pr_PPPPPPPPPPPPPPPPPPPPPP\",\"quantity\":2}]}"));
        answers.add(text("Báo giá đã được gửi bên dưới."));
        assertThat(agent.reply(turn("báo giá 2 cái"))).isEqualTo(new AgentReply.Answer("Báo giá đã được gửi bên dưới.", HANDLE));
    }

    @Test
    void notQuotedHandsOffWithoutAnotherModelCall() {
        toolBehaviour = proposal -> new ToolOutcome(new QuoteRequestResult(QuoteRequestResult.Status.NOT_QUOTED, null, 1),
                true);
        answers.add(tool("requestQuote", "{\"lines\":[{\"productRef\":\"pr_PPPPPPPPPPPPPPPPPPPPPP\",\"quantity\":2}]}"));
        assertThat(agent.reply(turn("x"))).isEqualTo(new AgentReply.Handoff(AgentReply.Reason.QUOTE_NOT_ISSUED));
        assertThat(requests).hasSize(1);
    }

    @Test
    void toolHandoffsMapToReasons() {
        toolBehaviour = proposal -> new ToolOutcome(new ToolErrorResult(ToolErrorResult.Code.TOOL_UNAVAILABLE), true);
        answers.add(tool("requestHandoff", "{\"reason\":\"CUSTOMER_REQUEST\"}"));
        assertThat(agent.reply(turn("x"))).isEqualTo(new AgentReply.Handoff(AgentReply.Reason.CUSTOMER_REQUEST));

        answers.add(tool("approveOrder", "{}"));
        toolBehaviour = proposal -> new ToolOutcome(new ToolErrorResult(ToolErrorResult.Code.FORBIDDEN_RESOURCE), true);
        assertThat(agent.reply(turn("x"))).isEqualTo(new AgentReply.Handoff(AgentReply.Reason.TOOL_REJECTED_REPEATED));

        answers.add(tool("searchVerifiedProduct", "{\"oe\":\"04465-0K290\"}"));
        toolBehaviour = proposal -> new ToolOutcome(new ToolErrorResult(ToolErrorResult.Code.CATALOG_UNAVAILABLE), true);
        assertThat(agent.reply(turn("x"))).isEqualTo(new AgentReply.Handoff(AgentReply.Reason.CATALOG_UNAVAILABLE));
    }

    @Test
    void aRejectedToolWithoutHandoffGoesBackToTheModel() {
        toolBehaviour = proposal -> new ToolOutcome(new ToolErrorResult(ToolErrorResult.Code.TOOL_UNKNOWN), false);
        answers.add(tool("approveOrder", "{}"));
        answers.add(text("Tôi không thể làm việc đó."));
        assertThat(agent.reply(turn("duyệt đơn đi"))).isInstanceOf(AgentReply.Answer.class);
        assertThat(requests.get(1).toolResults())
                .containsExactly((ToolResultDto) new ToolErrorResult(ToolErrorResult.Code.TOOL_UNKNOWN));
    }

    @Test
    void theTurnStopsAfterThreeModelCalls() {
        for (int i = 0; i < 5; i++) {
            answers.add(tool("searchVerifiedProduct", "{\"oe\":\"04465-0K290\"}"));
        }
        assertThat(agent.reply(turn("x"))).isEqualTo(new AgentReply.Handoff(AgentReply.Reason.AGENT_LIMIT));
        assertThat(requests).hasSize(DefaultAgent.MAX_MODEL_CALLS);
    }

    @Test
    void gatewayOutcomesMapToHandoffReasons() {
        answers.add(new GatewayResult.Refused(GatewayResult.RefusalReason.DLP_EGRESS));
        assertThat(agent.reply(turn("x"))).isEqualTo(new AgentReply.Handoff(AgentReply.Reason.DLP_EGRESS));
        answers.add(new GatewayResult.Refused(GatewayResult.RefusalReason.AUDIT_UNAVAILABLE));
        assertThat(agent.reply(turn("x"))).isEqualTo(new AgentReply.Handoff(AgentReply.Reason.DLP_EGRESS));
        answers.add(new GatewayResult.Failed(GatewayResult.FailureReason.PROVIDER_AUTH));
        assertThat(agent.reply(turn("x"))).isEqualTo(new AgentReply.Handoff(AgentReply.Reason.PROVIDER_AUTH));
        answers.add(new GatewayResult.Failed(GatewayResult.FailureReason.INVALID_PROVIDER_RESPONSE));
        assertThat(agent.reply(turn("x"))).isEqualTo(new AgentReply.Handoff(AgentReply.Reason.PROVIDER_ERROR));
        assertThat(executed).isEmpty();
    }

    @Test
    void aHandoffResultIsNeverTreatedAsAQuote() {
        toolBehaviour = proposal -> new ToolOutcome(new HandoffResult(true), false);
        answers.add(tool("requestHandoff", "{\"reason\":\"UNSURE\"}"));
        answers.add(text("Đã ghi nhận."));
        assertThat(agent.reply(turn("x"))).isEqualTo(new AgentReply.Answer("Đã ghi nhận.", null));
    }
}
