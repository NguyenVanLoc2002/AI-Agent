package com.secureerp.archfixture.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;

/** ARCH-05b violation: an open interface that any module could implement. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public interface OpenEgressInterface {
}
