package com.secureerp.archfixture.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;

/** Compliant: the allowlisted numeric pair TechnicalContext.requestedQuantity. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record TechnicalContext(Integer requestedQuantity) {
}
