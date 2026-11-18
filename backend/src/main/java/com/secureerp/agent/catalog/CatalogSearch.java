package com.secureerp.agent.catalog;

import com.secureerp.agent.catalog.spi.ExactProductQuery;
import com.secureerp.agent.platform.ConversationId;
import java.util.List;
import java.util.Optional;

/**
 * Catalog lookup for the tool layer (EP-03): exact queries only, at most
 * {@link ExactProductQuery#MAX_RESULTS} matches, each with a coarse availability bucket and never a
 * stock number or price (ERP_CONNECTOR §3.2, §3.4).
 *
 * @throws com.secureerp.agent.catalog.spi.CatalogUnavailableException when the catalog source fails;
 *     the exception carries a code only
 */
public interface CatalogSearch {

    List<CatalogMatch> search(ExactProductQuery query);

    /**
     * Searches by the conversation's current, unexpired VIN ({@link VehicleContext}, EP-05). The VIN
     * never leaves the catalog and ERP boundary.
     *
     * @return empty when the conversation has no current VIN; otherwise the matches (possibly none)
     */
    Optional<List<CatalogMatch>> searchByConversationVin(ConversationId conversation);
}
