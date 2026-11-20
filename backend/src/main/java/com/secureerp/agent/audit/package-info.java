/**
 * Module {@code audit}: field-names-only audit sink and owner of egress records.
 *
 * <p>DESIGN PROPOSAL (ADR-001, docs/architecture/MODULE_BOUNDARIES.md). Created by POC-001; SEC-01a adds
 * {@code egress_record}, EP-09b (A-45) the central {@code audit_event} ({@code AuditTrail}) and the read side
 * {@code AuditQueries}. Allowed module dependencies are enforced by
 * {@code ModuleStructureTest}; finer-grained rules (ARCH-xx) by {@code ArchitectureRulesTest}.
 */
@ApplicationModule(displayName = "Audit", allowedDependencies = { "platform" })
package com.secureerp.agent.audit;

import org.springframework.modulith.ApplicationModule;
