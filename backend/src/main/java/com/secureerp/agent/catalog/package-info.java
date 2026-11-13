/**
 * Module {@code catalog}: verified product lookup, provenance, coarse availability bucket.
 *
 * <p>DESIGN PROPOSAL (ADR-001, docs/architecture/MODULE_BOUNDARIES.md). Skeleton created by
 * POC-001; no business logic yet. Allowed module dependencies are enforced by
 * {@code ModuleStructureTest}; finer-grained rules (ARCH-xx) by {@code ArchitectureRulesTest}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Catalog", allowedDependencies = { "identity", "platform", "audit" })
package com.secureerp.agent.catalog;
