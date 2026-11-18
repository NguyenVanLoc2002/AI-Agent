package com.secureerp.agent.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;

/** Whether a handoff to staff was accepted. No reason code reaches the model. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record HandoffResult(boolean accepted) implements ToolResultDto {
}
