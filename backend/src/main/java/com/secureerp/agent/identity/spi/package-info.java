/**
 * Identity SPI (A-19 APPROVED 2026-11-18): the opaque ERP customer link that the {@code erp} adapter
 * understands. Exposed as the Modulith named interface {@code identity :: spi}; ARCH-08d limits its
 * use to identity, quote and erp.
 */
@NamedInterface("spi")
package com.secureerp.agent.identity.spi;

import org.springframework.modulith.NamedInterface;
