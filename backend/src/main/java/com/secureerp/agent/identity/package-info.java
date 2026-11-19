/**
 * Module {@code identity}: authenticated principal, roles, channel binding to an opaque customer reference.
 *
 * <p>EP-06 (A-19): {@code CustomerLinks} (read, quote only, ARCH-08e) and {@code CustomerLinkBinding}
 * (identity only, ARCH-08f) over {@code customer_link} (Flyway V7); the ERP key type is in
 * {@code identity :: spi}. Session authentication and the channel binding lookup come with EP-04.
 * Allowed module dependencies are enforced by {@code ModuleStructureTest}; finer-grained rules (ARCH-xx)
 * by {@code ArchitectureRulesTest}.
 */
@ApplicationModule(displayName = "Identity", allowedDependencies = { "platform", "audit" })
package com.secureerp.agent.identity;

import org.springframework.modulith.ApplicationModule;
