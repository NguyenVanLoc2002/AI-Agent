/**
 * Named interface {@code audit :: queries}: the read side of audit ({@code AuditQueries}), for the security
 * inspector only (EP-09b round-2 review M1: staff and customer pseudonyms, roles and trace IDs are not for
 * business modules). Enforced by the allowlists ({@code ModuleStructureTest}) and ARCH-13e.
 */
@NamedInterface("queries")
package com.secureerp.agent.audit.queries;

import org.springframework.modulith.NamedInterface;
