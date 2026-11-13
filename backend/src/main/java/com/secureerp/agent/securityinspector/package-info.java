/**
 * Module {@code securityinspector}: read-only redacted traces for SECURITY_REVIEWER.
 *
 * <p>DESIGN PROPOSAL (ADR-001, docs/architecture/MODULE_BOUNDARIES.md). Skeleton created by
 * POC-001; no business logic yet. Allowed module dependencies are enforced by
 * {@code ModuleStructureTest}; finer-grained rules (ARCH-xx) by {@code ArchitectureRulesTest}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Security Inspector", allowedDependencies = { "audit", "identity", "platform" })
package com.secureerp.agent.securityinspector;
