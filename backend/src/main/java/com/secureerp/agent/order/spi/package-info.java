/**
 * Order SPI (ADR-005, ADR-009; ERP_CONNECTOR §3.6): the draft-order port the {@code erp} adapter
 * implements. Only {@code order} and {@code erp} may depend on this package (ARCH-02b). Exposed as the
 * Modulith named interface {@code order :: spi}.
 */
@NamedInterface("spi")
package com.secureerp.agent.order.spi;

import org.springframework.modulith.NamedInterface;
