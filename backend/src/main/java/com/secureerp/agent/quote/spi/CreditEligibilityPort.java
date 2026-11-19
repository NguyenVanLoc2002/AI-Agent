package com.secureerp.agent.quote.spi;

import com.secureerp.agent.identity.spi.ErpCustomerLink;

/**
 * Credit eligibility of the authenticated customer (ERP_CONNECTOR §3.5, BRD §12 signature: customer
 * only). RESTRICTED: the status never reaches the model (BR-35); checking against the order total is
 * OPEN (D-01).
 */
public interface CreditEligibilityPort {

    /** @throws QuoteSourceUnavailableException when the ERP fails; carries no ERP message */
    EligibilityStatus check(ErpCustomerLink customer);
}
