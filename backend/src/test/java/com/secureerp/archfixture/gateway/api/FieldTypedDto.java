package com.secureerp.archfixture.gateway.api;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;

/** ARCH-05e violation (third review L4): Id.CLASS on a record component instead of the type. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record FieldTypedDto(@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS) CompliantStatus status) {
}
