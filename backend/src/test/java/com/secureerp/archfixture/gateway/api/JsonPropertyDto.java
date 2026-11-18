package com.secureerp.archfixture.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

/** ARCH-05c violation: Jackson annotations add properties the field rules never see. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record JsonPropertyDto(String sku) {

    @JsonProperty("unitPrice")
    @JsonRawValue
    public String extra() {
        return "1";
    }
}
