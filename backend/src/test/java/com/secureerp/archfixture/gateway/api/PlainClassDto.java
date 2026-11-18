package com.secureerp.archfixture.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;

/** ARCH-05b violation: a mutable plain class instead of a record. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public final class PlainClassDto {

    private String sku;

    public String getSku() {
        return sku;
    }
}
