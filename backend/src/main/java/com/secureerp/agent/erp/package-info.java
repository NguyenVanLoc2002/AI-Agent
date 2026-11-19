/**
 * Module {@code erp}: leaf adapter implementing module SPIs (Mock ERP in the PoC; a real adapter is gated
 * by D-01..D-03).
 *
 * <p>Implements {@code catalog :: spi} (SEC-01a, EP-05) and {@code quote :: spi} (EP-06) and {@code order :: spi} (EP-07); sees customers
 * only as {@code identity :: spi} {@code ErpCustomerLink} (A-19). Allowed module dependencies are
 * enforced by {@code ModuleStructureTest}; finer-grained rules (ARCH-xx) by {@code ArchitectureRulesTest}.
 */
@ApplicationModule(displayName = "ERP Adapter", allowedDependencies = { "catalog :: spi", "quote :: spi", "order :: spi", "identity :: spi", "platform", "audit" })
package com.secureerp.agent.erp;

import org.springframework.modulith.ApplicationModule;
