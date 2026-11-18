package com.secureerp.archfixture.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;

/** ARCH-05 violation (review M5): a stock number under the formerly allowlisted name quantity. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record ProductRefLike(String sku, Integer quantity) {
}
