/**
 * Egress DTOs (AI_GATEWAY §1, ADR-003): the only types that may be serialized towards a model
 * provider. ARCH-05 and ARCH-06 apply to this package. Exposed as the Modulith named interface
 * {@code gateway :: api}.
 */
@org.springframework.modulith.NamedInterface("api")
package com.secureerp.agent.gateway.api;
