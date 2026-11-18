package com.secureerp.agent.quote.spi;

import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.identity.AuthenticatedCustomerRef;
import java.util.Optional;

/**
 * Customer price for one SKU (ERP_CONNECTOR §3.3). RESTRICTED: the price never leaves the quote
 * module except on the server-rendered quote card.
 */
public interface PricingPort {

    /**
     * @return empty when the ERP has no price for this customer and SKU ({@code PRICE_NOT_FOUND})
     * @throws QuoteSourceUnavailableException when the ERP fails; carries no ERP message
     */
    Optional<PriceQuote> priceFor(AuthenticatedCustomerRef customer, Sku sku);
}
