package com.secureerp.agent.quote.spi;

import com.secureerp.agent.catalog.spi.Sku;
import java.util.OptionalInt;

/**
 * Stock quantity for one SKU (ERP_CONNECTOR §3.4). INTERNAL: quote compares it with the requested
 * quantity and never passes it on; the model only ever sees ISSUED or NOT_QUOTED.
 */
public interface AvailabilityPort {

    /**
     * @return empty when the ERP does not know the quantity (treated as not available)
     * @throws QuoteSourceUnavailableException when the ERP fails; carries no ERP message
     */
    OptionalInt quantityAvailable(Sku sku);
}
