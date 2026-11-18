package com.secureerp.archfixture.gateway.api;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;

/** Compliant: logical type names only. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record NameTypedDto(String sku) {
}
