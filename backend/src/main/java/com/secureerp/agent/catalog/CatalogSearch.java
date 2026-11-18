package com.secureerp.agent.catalog;

import com.secureerp.agent.catalog.spi.ExactProductQuery;
import java.util.List;

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
}
