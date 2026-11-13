/**
 * Module {@code erp}: leaf adapter implementing module SPIs (Mock Arito in PoC; real Arito gated by D-01..D-03).
 *
 * <p>DESIGN PROPOSAL (ADR-001, docs/architecture/MODULE_BOUNDARIES.md). Skeleton created by
 * POC-001; no business logic yet. Allowed module dependencies are enforced by
 * {@code ModuleStructureTest}; finer-grained rules (ARCH-xx) by {@code ArchitectureRulesTest}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "ERP Adapter", allowedDependencies = { "identity", "catalog", "quote", "order", "platform", "audit" })
package com.secureerp.agent.erp;
