/**
 * Catalog SPI (ADR-005): ports the {@code erp} adapter implements. Exposed as the Modulith named
 * interface {@code catalog :: spi}; only modules whose allowlist names it may use it.
 */
@NamedInterface("spi")
package com.secureerp.agent.catalog.spi;

import org.springframework.modulith.NamedInterface;
