package com.secureerp.archfixture.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;
import com.secureerp.archfixture.quote.internal.QuoteRow;

/** ARCH-05 violation: nests a type from outside the egress packages. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record NestedForeignDto(QuoteRow row) {
}
