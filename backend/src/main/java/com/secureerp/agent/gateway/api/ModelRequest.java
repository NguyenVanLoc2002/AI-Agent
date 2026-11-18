package com.secureerp.agent.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The only type the gateway accepts for a provider call (AI_GATEWAY §1, rule 30 §1, ADR-003).
 *
 * <p>Every field is allowlisted. There is no field for prices, stock numbers, customer identity,
 * real quote IDs or free-form JSON. The final serialized bytes are still scanned by the egress
 * policy before anything is sent.
 *
 * @param traceId        pseudonymous, server-generated (UUID)
 * @param systemPromptId reference to a versioned prompt template, never prompt content
 */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record ModelRequest(
        String traceId,
        String systemPromptId,
        List<SafeMessage> messages,
        TechnicalContext context,
        List<ToolResultDto> toolResults,
        List<ToolSpec> tools,
        ResponseBudget budget) {

    public static final int MAX_MESSAGES = 20;
    public static final int MAX_TOOL_RESULTS = 10;
    public static final int MAX_TOOLS = 10;

    static final Pattern TRACE_ID =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    static final Pattern PROMPT_ID = Pattern.compile("^[a-z][a-z0-9._-]{0,63}$");

    public ModelRequest {
        EgressText.matching(traceId, "traceId", TRACE_ID);
        EgressText.matching(systemPromptId, "systemPromptId", PROMPT_ID);
        messages = EgressText.list(messages, "messages", MAX_MESSAGES);
        if (messages.isEmpty()) {
            throw EgressText.invalid("messages");
        }
        toolResults = EgressText.list(toolResults, "toolResults", MAX_TOOL_RESULTS);
        tools = EgressText.list(tools, "tools", MAX_TOOLS);
        Objects.requireNonNull(budget, "budget");
    }
}
