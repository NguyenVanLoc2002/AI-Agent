package com.secureerp.archfixture.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;

/** ARCH-06 violation: classified, but not APPROVED_TECHNICAL. */
@EgressClassified(DataClassification.INTERNAL)
public record InternalClassifiedDto(String sku) {
}
