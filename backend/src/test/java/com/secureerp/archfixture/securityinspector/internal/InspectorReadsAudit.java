package com.secureerp.archfixture.securityinspector.internal;

import com.secureerp.archfixture.audit.queries.AuditQueries;

/** Compliant ARCH-13e: the security inspector reads the audit read port. */
public class InspectorReadsAudit {
    public Object read(AuditQueries queries) {
        return queries.events();
    }
}
