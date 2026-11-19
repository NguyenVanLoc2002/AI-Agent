/**
 * Module {@code order}: idempotent draft orders, unknown-outcome handling, reconciliation (EP-07;
 * ADR-007, ADR-009 ACCEPTED; DATA_FLOW §2). {@code draft_order_request} is the source of truth for
 * the ERP outcome; quote mirrors it. The ERP is reached only through {@code order :: spi}.
 *
 * <p>Allowed module dependencies are enforced by {@code ModuleStructureTest}; finer-grained rules
 * (ARCH-xx) by {@code ArchitectureRulesTest}.
 */
@ApplicationModule(displayName = "Order", allowedDependencies = { "identity", "identity :: spi", "platform", "audit" })
package com.secureerp.agent.order;

import org.springframework.modulith.ApplicationModule;
