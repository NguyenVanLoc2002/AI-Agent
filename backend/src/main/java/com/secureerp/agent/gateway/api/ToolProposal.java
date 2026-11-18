package com.secureerp.agent.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A tool call proposed by the model. Untrusted and non-binding: the tool layer validates name,
 * arguments, authorization and quotas again for every call (rule 30 §4, TOOL_CONTRACTS).
 */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record ToolProposal(String name, String argumentsJson) {

    public static final int MAX_ARGUMENT_BYTES = 4096;

    public ToolProposal {
        EgressText.matching(name, "name", ToolSpec.NAME);
        Objects.requireNonNull(argumentsJson, "argumentsJson");
        if (argumentsJson.getBytes(StandardCharsets.UTF_8).length > MAX_ARGUMENT_BYTES) {
            throw EgressText.invalid("argumentsJson");
        }
    }
}
