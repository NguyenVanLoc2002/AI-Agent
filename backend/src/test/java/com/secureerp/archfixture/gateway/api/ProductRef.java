package com.secureerp.archfixture.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;

/** Compliant: ProductRef.productRef is an approved server-identifier field. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record ProductRef(String productRef) {
}
