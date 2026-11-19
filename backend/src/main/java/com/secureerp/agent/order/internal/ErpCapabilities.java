package com.secureerp.agent.order.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * What the target ERP supports for draft orders (ADR-009, ERP_CONNECTOR §3.6). Static configuration
 * set from approved ERP API documentation, never reported by the adapter at run time (T-ERP-05). Both
 * are OPEN (D-03), so the safe default is false: no key is forwarded, no lookup is made, and every
 * unknown outcome goes to staff.
 */
@ConfigurationProperties("apas.erp.capabilities")
record ErpCapabilities(
        @DefaultValue("false") boolean supportsIdempotencyKey,
        @DefaultValue("false") boolean supportsExternalRefLookup) {
}
