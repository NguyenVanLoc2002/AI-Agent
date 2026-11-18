package com.secureerp.archfixture.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;
import com.secureerp.archfixture.support.LeakyBase;

/** ARCH-05b violation (review H3): inherits a money field ARCH-05 does not see. */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public final class InheritingDto extends LeakyBase {
}
