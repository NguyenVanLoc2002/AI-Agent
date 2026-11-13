/**
 * Module {@code platform}: cross-cutting technical concerns: clock, correlation ID, error mapping, kill switch.
 *
 * <p>DESIGN PROPOSAL (ADR-001, docs/architecture/MODULE_BOUNDARIES.md). Skeleton created by
 * POC-001; no business logic yet. Allowed module dependencies are enforced by
 * {@code ModuleStructureTest}; finer-grained rules (ARCH-xx) by {@code ArchitectureRulesTest}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Platform", allowedDependencies = {  })
package com.secureerp.agent.platform;
