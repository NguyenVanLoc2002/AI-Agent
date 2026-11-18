package com.secureerp.archfixture.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;
import com.secureerp.archfixture.identity.AuthenticatedCustomerRef;

/** ARCH-05 violation: customer identity in an egress type. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record CustomerRefDto(AuthenticatedCustomerRef customer) {
}
