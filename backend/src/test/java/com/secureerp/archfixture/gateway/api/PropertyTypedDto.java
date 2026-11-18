package com.secureerp.archfixture.gateway.api;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;

/** ARCH-05e violation (third review L4): the type ID would travel under a scan-exempt field name. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "quoteHandle")
public record PropertyTypedDto(String sku) {
}
