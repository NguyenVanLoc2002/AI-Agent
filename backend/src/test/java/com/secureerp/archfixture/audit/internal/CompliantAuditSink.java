package com.secureerp.archfixture.audit.internal;

import java.util.List;

/** Compliant ARCH-10: field names only, no business dependency. */
public class CompliantAuditSink {
    public int record(String eventType, List<String> fieldNames) {
        return fieldNames.size() + eventType.length();
    }
}
