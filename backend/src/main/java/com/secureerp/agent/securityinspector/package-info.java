/**
 * Module {@code securityinspector}: read-only redacted traces for SECURITY_REVIEWER.
 *
 * <p>DESIGN PROPOSAL (ADR-001, docs/architecture/MODULE_BOUNDARIES.md). Skeleton created by
 * POC-001; EP-09b (A-45) adds {@code SecurityInspector} and {@code /api/demo/security/**}: egress records,
 * tool calls ({@code tools :: history}), central audit events and kill switch changes, metadata only. Allowed module dependencies are enforced by
 * {@code ModuleStructureTest}; finer-grained rules (ARCH-xx) by {@code ArchitectureRulesTest}.
 */
@ApplicationModule(displayName = "Security Inspector", allowedDependencies = { "audit", "audit :: queries", "identity", "platform",
        "platform :: history", "tools :: history" })
package com.secureerp.agent.securityinspector;

import org.springframework.modulith.ApplicationModule;
