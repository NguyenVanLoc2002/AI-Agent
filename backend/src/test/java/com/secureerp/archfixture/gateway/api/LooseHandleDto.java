package com.secureerp.archfixture.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;

/**
 * ARCH-05d violation (second review M2): a field named like a server identifier gets only the
 * honeytoken check at egress, so it may exist only on the approved, strictly validated types.
 */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record LooseHandleDto(String quoteHandle) {
}
