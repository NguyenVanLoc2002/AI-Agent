package com.secureerp.agent.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;
import java.util.Objects;

/**
 * A rejected or failed tool call as the model sees it: a stable code only (TOOL_CONTRACTS §1, §5).
 * No message, no field value, no reason detail beyond the code.
 */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record ToolErrorResult(Code code) implements ToolResultDto {

    public enum Code {
        TOOL_UNKNOWN,
        TOOL_SCHEMA,
        FORBIDDEN_RESOURCE,
        QUOTA_EXCEEDED,
        CATALOG_UNAVAILABLE,
        TOOL_UNAVAILABLE
    }

    public ToolErrorResult {
        Objects.requireNonNull(code, "code");
    }
}
