package com.secureerp.agent.gateway.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;

/**
 * Typed tool results the model may see (AI_GATEWAY §1). Closed set: bounded enums and codes only,
 * no free text. The type discriminator uses fixed names, never class names or default typing.
 */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ProductSearchResult.class, name = "productSearch"),
    @JsonSubTypes.Type(value = QuoteRequestResult.class, name = "quoteRequest"),
    @JsonSubTypes.Type(value = HandoffResult.class, name = "handoff")
})
public sealed interface ToolResultDto permits ProductSearchResult, QuoteRequestResult, HandoffResult {
}
