package com.secureerp.archfixture.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** ARCH-05 violation: Map, Object and a generic argument carrying money. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record MapCarrierDto(Map<String, String> extra, List<BigDecimal> prices, Object payload) {
}
