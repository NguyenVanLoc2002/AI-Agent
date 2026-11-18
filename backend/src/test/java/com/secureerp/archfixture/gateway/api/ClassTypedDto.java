package com.secureerp.archfixture.gateway.api;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;

/** ARCH-05e violation (second review L5): Id.CLASS writes Java class names into the payload. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public record ClassTypedDto(String sku) {
}
