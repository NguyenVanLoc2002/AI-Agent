package com.secureerp.archfixture.operations.internal;

import com.secureerp.archfixture.audit.queries.AuditQueries;

/** Violation ARCH-13e: a module other than securityinspector reads the audit read port. */
public class OperatorReadsAudit {
    public Object peek(AuditQueries queries) {
        return queries.events();
    }
}
