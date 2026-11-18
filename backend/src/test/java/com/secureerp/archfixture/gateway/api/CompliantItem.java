package com.secureerp.archfixture.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;

/** Compliant nested egress record. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record CompliantItem(String oe) {
}
