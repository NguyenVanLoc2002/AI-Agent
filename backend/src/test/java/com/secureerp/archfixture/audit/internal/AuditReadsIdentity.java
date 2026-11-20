package com.secureerp.archfixture.audit.internal;

import com.secureerp.archfixture.identity.internal.CustomerLinkResolver;

/** Violation ARCH-10 (EP-09b round-2 review L6): audit depends on identity. */
public class AuditReadsIdentity {
    public Object peek(CustomerLinkResolver resolver) {
        return resolver;
    }
}
