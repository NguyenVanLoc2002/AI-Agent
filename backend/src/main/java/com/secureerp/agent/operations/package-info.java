/**
 * Module {@code operations}: operator controls (UC-08, ADR-013 ACCEPTED 2026-11-20, A-44). EP-09a: the AI
 * kill switch and closing the provider-auth breaker, for OPS only. The switch itself is
 * {@code platform.KillSwitch}; this module only checks the operator and records the actor. An operator
 * reads no business data here (BR-24).
 */
@ApplicationModule(displayName = "Operations", allowedDependencies = { "identity", "platform" })
package com.secureerp.agent.operations;

import org.springframework.modulith.ApplicationModule;
