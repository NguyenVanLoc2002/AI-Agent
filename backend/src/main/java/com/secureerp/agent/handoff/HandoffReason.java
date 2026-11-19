package com.secureerp.agent.handoff;

/**
 * Why a conversation went to staff (THREAT_MODEL §5, DESIGN PROPOSAL). Internal: shown to SALES only
 * (EP-08), never to the model or the customer. EP-04 uses the codes its turn pipeline can produce.
 */
public enum HandoffReason {
    DLP_INBOUND,
    DLP_EGRESS,
    PROMPT_INJECTION,
    TOOL_REJECTED_REPEATED,
    PROVIDER_ERROR,
    PROVIDER_AUTH,
    CATALOG_UNAVAILABLE,
    QUOTE_NOT_ISSUED,
    CUSTOMER_REQUEST,
    UNSURE,
    OUT_OF_SCOPE,
    /** EP-04: the model's final text failed the OutputValidator (DESIGN PROPOSAL, decision 2026-11-19). */
    OUTPUT_REJECTED,
    /** EP-04: no final answer within the model-call limit of one turn (DESIGN PROPOSAL). */
    AGENT_LIMIT
}
