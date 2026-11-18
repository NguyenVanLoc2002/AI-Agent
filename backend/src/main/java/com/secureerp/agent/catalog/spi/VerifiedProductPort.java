package com.secureerp.agent.catalog.spi;

import java.util.List;

/**
 * Verified product lookup (ERP_CONNECTOR §3.2, ADR-005 ACCEPTED 2026-11-18). Implemented by the
 * {@code erp} adapter. Failures surface as {@link CatalogUnavailableException}, which carries only
 * an error code.
 */
public interface VerifiedProductPort {

    /** At most {@link ExactProductQuery#MAX_RESULTS} candidates, without duplicates. */
    List<ProductCandidate> search(ExactProductQuery query);
}
