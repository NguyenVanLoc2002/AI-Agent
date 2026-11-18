package com.secureerp.agent.platform;

/**
 * Data classes from rule 10 and docs/security/DATA_CLASSIFICATION.md §1.
 *
 * <p>Only {@link #APPROVED_TECHNICAL} may be placed in a type that leaves the application towards a
 * model provider (ADR-003, ARCH-06).
 */
public enum DataClassification {
    RESTRICTED,
    INTERNAL,
    APPROVED_TECHNICAL
}
