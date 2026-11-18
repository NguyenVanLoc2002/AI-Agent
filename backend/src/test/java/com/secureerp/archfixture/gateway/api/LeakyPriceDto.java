package com.secureerp.archfixture.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;
import java.math.BigDecimal;

/** ARCH-05 violation: money and a non-allowlisted number. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record LeakyPriceDto(String sku, long unitPrice, BigDecimal total) {
}
