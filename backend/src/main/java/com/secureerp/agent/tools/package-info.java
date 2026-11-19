/**
 * Module {@code tools}: tool registry, strict schemas, per-call authorization and quotas.
 *
 * <p>EP-03 registry, EP-05 vinRef search, EP-06 requestQuote through {@code quote.QuoteService} (never the
 * price-bearing card, ARCH-13a). Allowed module dependencies are enforced by {@code ModuleStructureTest};
 * finer-grained rules (ARCH-xx) by {@code ArchitectureRulesTest}.
 */
@ApplicationModule(displayName = "Tools", allowedDependencies = { "catalog", "catalog :: spi", "quote", "gateway :: api", "identity", "platform" })
package com.secureerp.agent.tools;

import org.springframework.modulith.ApplicationModule;
