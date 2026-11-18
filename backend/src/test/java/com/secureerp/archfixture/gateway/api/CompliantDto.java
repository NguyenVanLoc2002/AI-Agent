package com.secureerp.archfixture.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;
import java.util.List;

/** Compliant egress record. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record CompliantDto(String sku, List<CompliantItem> items, boolean accepted, CompliantStatus status) {
}
