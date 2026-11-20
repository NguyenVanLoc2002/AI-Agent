/**
 * Module {@code platform}: cross-cutting technical concerns: clock, correlation ID, error mapping, kill switch.
 *
 * <p>DESIGN PROPOSAL (ADR-001, docs/architecture/MODULE_BOUNDARIES.md). Created by POC-001; EP-09a adds the
 * stateful kill switch ({@code KillSwitch}, tables {@code feature_switch} / {@code feature_switch_event},
 * ADR-013). It depends on no other module. Allowed module dependencies are enforced by
 * {@code ModuleStructureTest}; finer-grained rules (ARCH-xx) by {@code ArchitectureRulesTest}.
 */
@ApplicationModule(displayName = "Platform", allowedDependencies = {  })
package com.secureerp.agent.platform;

import org.springframework.modulith.ApplicationModule;
