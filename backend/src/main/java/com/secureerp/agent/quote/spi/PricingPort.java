package com.secureerp.agent.quote.spi;

import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.identity.spi.ErpCustomerLink;
import java.util.Optional;

/**
 * Customer price for one SKU (ERP_CONNECTOR §3.3). RESTRICTED: the price never leaves the quote
 * module except on the server-rendered quote card. The customer is the ERP link that quote got from
 * identity (A-19): the adapter never sees the session reference.
 */
public interface PricingPort {

    /**
     * @return empty when the ERP has no price for this customer and SKU ({@code PRICE_NOT_FOUND})
     * @throws QuoteSourceUnavailableException when the ERP fails; carries no ERP message
     */
    Optional<PriceQuote> priceFor(ErpCustomerLink customer, Sku sku);
}
