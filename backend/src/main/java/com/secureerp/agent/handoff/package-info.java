/**
 * Module {@code handoff}: handoff queue, reason codes, claim/resolve; publishes events consumed by conversation.
 *
 * <p>EP-04: minimal OPEN record with an internal reason code (decision 2026-11-19); queue, claim and
 * resolve are EP-08. Allowed module dependencies are enforced by
 * {@code ModuleStructureTest}; finer-grained rules (ARCH-xx) by {@code ArchitectureRulesTest}.
 */
@ApplicationModule(displayName = "Handoff", allowedDependencies = { "identity", "platform", "audit" })
package com.secureerp.agent.handoff;

import org.springframework.modulith.ApplicationModule;
