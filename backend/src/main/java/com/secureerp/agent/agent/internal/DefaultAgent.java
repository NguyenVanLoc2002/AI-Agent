package com.secureerp.agent.agent.internal;

import com.secureerp.agent.agent.Agent;
import com.secureerp.agent.agent.AgentReply;
import com.secureerp.agent.agent.AgentReply.Reason;
import com.secureerp.agent.agent.AgentTurn;
import com.secureerp.agent.gateway.AiGateway;
import com.secureerp.agent.gateway.GatewayResult;
import com.secureerp.agent.gateway.api.ModelRequest;
import com.secureerp.agent.gateway.api.ModelResponse;
import com.secureerp.agent.gateway.api.ProductRef;
import com.secureerp.agent.gateway.api.ProductSearchResult;
import com.secureerp.agent.gateway.api.QuoteRequestResult;
import com.secureerp.agent.gateway.api.ResponseBudget;
import com.secureerp.agent.gateway.api.SafeMessage;
import com.secureerp.agent.gateway.api.TechnicalContext;
import com.secureerp.agent.gateway.api.ToolErrorResult;
import com.secureerp.agent.gateway.api.ToolProposal;
import com.secureerp.agent.gateway.api.ToolResultDto;
import com.secureerp.agent.gateway.api.ToolSpec;
import com.secureerp.agent.platform.KillSwitch;
import com.secureerp.agent.tools.ToolCallContext;
import com.secureerp.agent.tools.ToolManager;
import com.secureerp.agent.tools.ToolOutcome;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Bounded agent loop (EP-04; decisions 2026-11-19): at most {@value #MAX_MODEL_CALLS} model calls per
 * turn; every tool proposal goes through {@link ToolManager} (which enforces its own per-turn limit); a
 * handoff from a tool ends the turn without another model call; the final text must pass the
 * {@link OutputValidator}, otherwise it is dropped and the turn is handed off.
 *
 * <p>History (ADR-011 ACCEPTED 2026-11-19) is deterministic: earlier sanitized customer messages become
 * one CONTEXT_SUMMARY segment, the current one a CUSTOMER segment. Bot text, quote cards and staff
 * messages never enter it, and no model call is used to summarise. Products found in earlier turns are
 * not carried over (the model may search again from the OE in the history); DESIGN PROPOSAL.
 *
 * <p>Logs carry counts, codes and rejection kinds only, never text.
 */
@Service
class DefaultAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgent.class);

    static final int MAX_MODEL_CALLS = 3;
    static final String SYSTEM_PROMPT_ID = "apas.chat.v1";
    private static final String SUMMARY_HEADER = "Tin nhắn trước của khách (đã lọc):";

    private final AiGateway gateway;
    private final ToolManager tools;
    private final KillSwitch killSwitch;

    DefaultAgent(AiGateway gateway, ToolManager tools, KillSwitch killSwitch) {
        this.gateway = gateway;
        this.tools = tools;
        this.killSwitch = killSwitch;
    }

    /**
     * EP-09a reviews (security L1, backend L3): a switch turned off while the model answered stops the tools
     * it proposed too (no quote is issued that nobody will see); fails closed like the gateway.
     */
    private Optional<Reason> switchedOff() {
        KillSwitch.State state;
        try {
            state = Objects.requireNonNull(killSwitch.current(), "state");
        } catch (RuntimeException e) {
            state = KillSwitch.State.unreadable();
        }
        if (state.aiEnabled()) {
            return Optional.empty();
        }
        return Optional.of(state.disabledReason() == KillSwitch.Reason.PROVIDER_AUTH
                ? Reason.PROVIDER_AUTH : Reason.AI_DISABLED);
    }

    @Override
    public AgentReply reply(AgentTurn turn) {
        List<SafeMessage> messages = history(turn.customerTexts());
        TechnicalContext context = new TechnicalContext(turn.maskedVin(), null, null, null, null, List.of(), null);
        List<ToolSpec> specs = tools.toolsFor(turn.role(), turn.channel());
        ToolCallContext toolContext = new ToolCallContext(turn.principal(), turn.role(), turn.channel(),
                turn.conversation(), turn.turnId(), turn.customer());
        List<ToolResultDto> results = new ArrayList<>();
        Set<String> allowedCodes = new HashSet<>();
        String issuedHandle = null;

        for (int call = 1; call <= MAX_MODEL_CALLS; call++) {
            ModelRequest request = new ModelRequest(UUID.randomUUID().toString(), SYSTEM_PROMPT_ID, messages, context,
                    results, specs, ResponseBudget.STANDARD);
            GatewayResult result = gateway.complete(request);
            Optional<Reason> failure = failure(result);
            if (failure.isPresent()) {
                return handoff(failure.get(), call);
            }
            ModelResponse response = ((GatewayResult.Completed) result).response();
            if (response.toolProposals().isEmpty()) {
                Optional<OutputValidator.Rejection> rejection = OutputValidator.check(response.text(), allowedCodes);
                if (rejection.isPresent()) {
                    log.warn("agent.turn output_rejected kind={} call={}", rejection.get(), call);
                    return handoff(Reason.OUTPUT_REJECTED, call);
                }
                log.info("agent.turn answered calls={} toolResults={} quote={}", call, results.size(),
                        issuedHandle != null);
                return new AgentReply.Answer(response.text().strip(), issuedHandle);
            }
            Optional<Reason> off = switchedOff();
            if (off.isPresent()) {
                return handoff(off.get(), call);
            }
            for (ToolProposal proposal : response.toolProposals()) {
                if (results.size() >= ModelRequest.MAX_TOOL_RESULTS) {
                    return handoff(Reason.TOOL_REJECTED_REPEATED, call);
                }
                ToolOutcome outcome = tools.execute(toolContext, proposal);
                results.add(outcome.result());
                collectCodes(outcome.result(), allowedCodes);
                if (outcome.result() instanceof QuoteRequestResult quote
                        && quote.status() == QuoteRequestResult.Status.ISSUED) {
                    issuedHandle = quote.quoteHandle();
                }
                if (outcome.handoffRequired()) {
                    return handoff(toolHandoffReason(outcome), call);
                }
            }
        }
        return handoff(Reason.AGENT_LIMIT, MAX_MODEL_CALLS);
    }

    /** ADR-011: deterministic, customer-only history. The last text is the current message. */
    static List<SafeMessage> history(List<String> customerTexts) {
        List<SafeMessage> messages = new ArrayList<>(2);
        List<String> earlier = customerTexts.subList(0, customerTexts.size() - 1);
        if (!earlier.isEmpty()) {
            StringBuilder summary = new StringBuilder(SUMMARY_HEADER);
            // Newest first until the segment limit, then restore chronological order.
            List<String> kept = new ArrayList<>();
            int budget = SafeMessage.MAX_TEXT_CHARS - SUMMARY_HEADER.length();
            for (int i = earlier.size() - 1; i >= 0; i--) {
                String line = "\n- " + earlier.get(i);
                if (line.length() > budget) {
                    break;
                }
                budget -= line.length();
                kept.addFirst(line);
            }
            kept.forEach(summary::append);
            if (!kept.isEmpty()) {
                messages.add(new SafeMessage(SafeMessage.Role.CONTEXT_SUMMARY, summary.toString()));
            }
        }
        messages.add(new SafeMessage(SafeMessage.Role.CUSTOMER, customerTexts.getLast()));
        return messages;
    }

    private static Optional<Reason> failure(GatewayResult result) {
        return switch (result) {
            case GatewayResult.Completed completed -> Optional.empty();
            case GatewayResult.Refused refused -> Optional.of(switch (refused.reason()) {
                case DLP_EGRESS, AUDIT_UNAVAILABLE -> Reason.DLP_EGRESS;
                case AI_DISABLED -> Reason.AI_DISABLED;
                case PROVIDER_AUTH_BREAKER -> Reason.PROVIDER_AUTH;
                // SEC03-F3 (A-58): an answer withheld for a honeytoken is a rejected output.
                case OUTPUT_HONEYTOKEN -> Reason.OUTPUT_REJECTED;
            });
            case GatewayResult.Failed failed -> Optional.of(failed.reason() == GatewayResult.FailureReason.PROVIDER_AUTH
                    ? Reason.PROVIDER_AUTH : Reason.PROVIDER_ERROR);
        };
    }

    private static Reason toolHandoffReason(ToolOutcome outcome) {
        ToolResultDto result = outcome.result();
        if (outcome.requestedHandoff() != null) {
            // EP-08b: an accepted requestHandoff keeps the reason the model gave (TOOL_CONTRACTS §3.4).
            return switch (outcome.requestedHandoff()) {
                case CUSTOMER_REQUEST -> Reason.CUSTOMER_REQUEST;
                case UNSURE -> Reason.UNSURE;
                case OUT_OF_SCOPE -> Reason.OUT_OF_SCOPE;
            };
        }
        if (result instanceof QuoteRequestResult) {
            return Reason.QUOTE_NOT_ISSUED;
        }
        if (result instanceof ToolErrorResult error && error.code() == ToolErrorResult.Code.CATALOG_UNAVAILABLE) {
            return Reason.CATALOG_UNAVAILABLE;
        }
        // EP-08b reviews (security L-3, architecture L-4): an accepted requestHandoff carries its reason
        // above; a rejected one that hands off (quota, forbidden, repeated) is a rejection, not a request.
        return Reason.TOOL_REJECTED_REPEATED;
    }

    private static void collectCodes(ToolResultDto result, Set<String> allowedCodes) {
        if (result instanceof ProductSearchResult search) {
            for (ProductRef candidate : search.candidates()) {
                allowedCodes.add(candidate.sku().toUpperCase(Locale.ROOT));
                if (candidate.oe() != null) {
                    allowedCodes.add(candidate.oe().toUpperCase(Locale.ROOT));
                }
            }
        }
    }

    private static AgentReply handoff(Reason reason, int calls) {
        log.warn("agent.turn handoff reason={} calls={}", reason, calls);
        return new AgentReply.Handoff(reason);
    }
}
