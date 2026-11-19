/**
 * Module {@code quote}: automatic quotes (EP-06; ADR-007/008/014 ACCEPTED 2026-11-18). Price, stock
 * and credit are checked here through {@code quote :: spi}; the tool layer gets only an opaque handle
 * or NOT_QUOTED, and the price-bearing card is rendered on the server (ARCH-13a). Confirmation and draft
 * orders come with EP-07. Allowed module dependencies are enforced by {@code ModuleStructureTest};
 * finer-grained rules (ARCH-xx) by {@code ArchitectureRulesTest}.
 */
@ApplicationModule(displayName = "Quote", allowedDependencies = { "identity", "identity :: spi", "catalog :: spi", "platform", "audit" })
package com.secureerp.agent.quote;

import org.springframework.modulith.ApplicationModule;
