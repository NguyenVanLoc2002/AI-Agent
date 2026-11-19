/**
 * Module {@code agent}: agent loop, ModelRequest construction, policy-safe history summary, output validation.
 *
 * <p>EP-04: bounded agent loop, deterministic customer-only history (ADR-011), OutputValidator. Uses the
 * egress DTOs of {@code gateway :: api} to build requests and read tool results. Allowed module dependencies are enforced by
 * {@code ModuleStructureTest}; finer-grained rules (ARCH-xx) by {@code ArchitectureRulesTest}.
 */
@ApplicationModule(displayName = "Agent", allowedDependencies = { "gateway", "gateway :: api", "tools", "identity", "platform", "audit" })
package com.secureerp.agent.agent;

import org.springframework.modulith.ApplicationModule;
