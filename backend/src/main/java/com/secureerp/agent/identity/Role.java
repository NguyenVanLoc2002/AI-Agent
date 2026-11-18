package com.secureerp.agent.identity;

/** Roles from docs/security/RBAC_MATRIX.md. Resolved from the authenticated session (ADR-010). */
public enum Role {
    CUST,
    SALES,
    OPS,
    SECREV
}
