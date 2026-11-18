package com.secureerp.archfixture.gateway.api;

import com.secureerp.archfixture.support.DataClassification;
import com.secureerp.archfixture.support.EgressClassified;

/** ARCH-06 violation: classified with a lookalike annotation, not platform.EgressClassified. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record LookalikeClassifiedDto(String sku) {
}
