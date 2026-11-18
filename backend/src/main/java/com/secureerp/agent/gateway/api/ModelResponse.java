package com.secureerp.agent.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;
import java.util.List;

/**
 * Parsed provider output (AI_GATEWAY §1, §4). Untrusted: strictly parsed and size-bounded here.
 * Content checks (prices, invented OE codes, links) belong to the agent's OutputValidator (EP-04).
 */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record ModelResponse(String text, List<ToolProposal> toolProposals) {

    public static final int MAX_TEXT_CHARS = 4000;
    public static final int MAX_PROPOSALS = 5;

    public ModelResponse {
        EgressText.optionalText(text, "text", MAX_TEXT_CHARS);
        toolProposals = EgressText.list(toolProposals, "toolProposals", MAX_PROPOSALS);
        if (text == null && toolProposals.isEmpty()) {
            throw EgressText.invalid("text");
        }
    }
}
