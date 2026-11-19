/**
 * Quote SPI (ADR-005, ERP_CONNECTOR §3.3–3.5): pricing, stock and credit ports the {@code erp}
 * adapter implements. RESTRICTED/INTERNAL data: only {@code quote}, {@code order} and {@code erp} may
 * depend on this package (ARCH-02a). Exposed as the Modulith named interface {@code quote :: spi}.
 */
@NamedInterface("spi")
package com.secureerp.agent.quote.spi;

import org.springframework.modulith.NamedInterface;
