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
import com.secureerp.agent.platform.TestKillSwitch;
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

    private final TestKillSwitch killSwitch = new TestKillSwitch();
    private final DefaultAgent agent = new DefaultAgent(gateway, tools, killSwitch);

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

    /** A-90 (2A): a customer message about an order turns on the stricter order claim rule. */
    @Test
    void aDoneClaimIsDroppedWhenTheCustomerAsksAboutAnOrder() {
        answers.add(text("Anh yên tâm, đã được duyệt rồi"));
        assertThat(agent.reply(turn("má phanh trước", "đơn của tôi sao rồi")))
                .isEqualTo(new AgentReply.Handoff(AgentReply.Reason.OUTPUT_REJECTED));
    }

    /** A-90 (2A): an earlier customer message counts too. */
    @Test
    void anEarlierOrderMessageTurnsOnTheContext() {
        answers.add(text("Dạ duyệt rồi"));
        assertThat(agent.reply(turn("bao gia giup toi", "cảm ơn")))
                .isEqualTo(new AgentReply.Handoff(AgentReply.Reason.OUTPUT_REJECTED));
    }

    /** A-90: without an order or a quote in the turn, the stricter rule stays off (A-83 gap). */
    @Test
    void withoutAnOrderContextTheStricterRuleIsOff() {
        answers.add(text("Anh yên tâm, đã được duyệt rồi"));
        assertThat(agent.reply(turn("má phanh xe tôi")))
                .isEqualTo(new AgentReply.Answer("Anh yên tâm, đã được duyệt rồi", null));
    }

    /** A-90 (2A): a quote tool in this turn turns on the context, even when the customer names no order. */
    @Test
    void aQuoteToolTurnsOnTheContext() {
        toolBehaviour = proposal -> new ToolOutcome(new QuoteRequestResult(QuoteRequestResult.Status.ISSUED, HANDLE, 1),
                false);
        answers.add(tool("requestQuote", "{\"lines\":[{\"productRef\":\"pr_PPPPPPPPPPPPPPPPPPPPPP\",\"quantity\":2}]}"));
        answers.add(text("Dạ duyệt rồi ạ"));
        assertThat(agent.reply(turn("lấy 2 cái")))
                .isEqualTo(new AgentReply.Handoff(AgentReply.Reason.OUTPUT_REJECTED));
    }

    /** A-90 review (security M1, backend M1, QA H1): a rejected quote proposal turns the context on too. */
    @Test
    void aRejectedQuoteProposalTurnsOnTheContext() {
        toolBehaviour = proposal -> new ToolOutcome(new ToolErrorResult(ToolErrorResult.Code.TOOL_SCHEMA), false);
        answers.add(tool("requestQuote", "{}"));
        answers.add(text("Đã chốt rồi ạ"));
        assertThat(agent.reply(turn("lấy 2 cái")))
                .isEqualTo(new AgentReply.Handoff(AgentReply.Reason.OUTPUT_REJECTED));
    }

    /** A-90: another tool does not; the A-83 rules apply (a documented gap). */
    @Test
    void anotherToolDoesNotTurnOnTheContext() {
        toolBehaviour = proposal -> new ToolOutcome(new ToolErrorResult(ToolErrorResult.Code.TOOL_SCHEMA), false);
        answers.add(tool("searchVerifiedProduct", "{}"));
        answers.add(text("Dạ duyệt rồi"));
        assertThat(agent.reply(turn("má phanh trước")))
                .isEqualTo(new AgentReply.Answer("Dạ duyệt rồi", null));
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
        // EP-08b: an accepted requestHandoff keeps the reason the model gave.
        toolBehaviour = proposal ->
                new ToolOutcome(new HandoffResult(true), true, ToolOutcome.RequestedHandoff.OUT_OF_SCOPE);
        answers.add(tool("requestHandoff", "{\"reason\":\"OUT_OF_SCOPE\"}"));
        assertThat(agent.reply(turn("x"))).isEqualTo(new AgentReply.Handoff(AgentReply.Reason.OUT_OF_SCOPE));

        // EP-08b reviews: a rejected requestHandoff that hands off is a rejection, not a customer request.
        toolBehaviour = proposal -> new ToolOutcome(new ToolErrorResult(ToolErrorResult.Code.QUOTA_EXCEEDED), true);
        answers.add(tool("requestHandoff", "{\"reason\":\"CUSTOMER_REQUEST\"}"));
        assertThat(agent.reply(turn("x"))).isEqualTo(new AgentReply.Handoff(AgentReply.Reason.TOOL_REJECTED_REPEATED));

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
        // EP-09a: the kill switch and the provider-auth breaker.
        answers.add(new GatewayResult.Refused(GatewayResult.RefusalReason.AI_DISABLED));
        assertThat(agent.reply(turn("x"))).isEqualTo(new AgentReply.Handoff(AgentReply.Reason.AI_DISABLED));
        answers.add(new GatewayResult.Refused(GatewayResult.RefusalReason.PROVIDER_AUTH_BREAKER));
        assertThat(agent.reply(turn("x"))).isEqualTo(new AgentReply.Handoff(AgentReply.Reason.PROVIDER_AUTH));
        assertThat(executed).isEmpty();
    }

    /**
     * EP-09a reviews (security L1, backend L3, architecture M2): a switch turned off while the model answered
     * stops the tools it proposed; no quote is issued that nobody would see.
     */
    @Test
    void aSwitchTurnedOffBeforeTheToolsStopsThem() {
        answers.add(tool("searchVerifiedProduct", "{\"oe\":\"04465-0K290\"}"));
        killSwitch.set(false, "usr_AAAAAAAAAAAAAAAAAAAAAA");
        assertThat(agent.reply(turn("x"))).isEqualTo(new AgentReply.Handoff(AgentReply.Reason.AI_DISABLED));

        answers.add(tool("searchVerifiedProduct", "{\"oe\":\"04465-0K290\"}"));
        killSwitch.tripProviderAuth();
        assertThat(agent.reply(turn("x"))).isEqualTo(new AgentReply.Handoff(AgentReply.Reason.PROVIDER_AUTH));

        answers.add(tool("searchVerifiedProduct", "{\"oe\":\"04465-0K290\"}"));
        killSwitch.set(true, "usr_AAAAAAAAAAAAAAAAAAAAAA");
        killSwitch.throwOnRead = true;
        assertThat(agent.reply(turn("x"))).as("a throwing switch is off")
                .isEqualTo(new AgentReply.Handoff(AgentReply.Reason.AI_DISABLED));
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
