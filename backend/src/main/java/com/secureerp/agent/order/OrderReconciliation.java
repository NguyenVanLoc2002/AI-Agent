package com.secureerp.agent.order;

import com.secureerp.agent.identity.SessionIdentity;
import java.time.Instant;
import java.util.List;

/**
 * Manual reconciliation of draft orders whose ERP outcome is unknown (ADR-009, RBAC_MATRIX: SALES
 * only). Staff look the order up in the ERP by its external reference and record what they found;
 * nothing is ever sent to the ERP again from here. Every decision is stored with its actor.
 *
 * <p>Any SALES user may reconcile any request: team scoping needs the assignment model of EP-08
 * (D-08 OPEN).
 */
public interface OrderReconciliation {

    enum Finding {
        /** The draft exists in the ERP. */
        CREATED,
        /** The ERP has no such draft. */
        ABSENT
    }

    /**
     * A request waiting for reconciliation. No customer, no prices.
     *
     * @param reportedErpOrderRef the draft number the ERP reported after the request had already timed
     *                            out, or {@code null}; a decision that contradicts it is refused
     */
    record UnknownDraftOrder(long requestId, String quoteRef, int quoteVersion, String externalRef, Instant since,
            String reportedErpOrderRef) {
    }

    List<UnknownDraftOrder> unknown(SessionIdentity staff);

    /**
     * @param erpOrderRef the ERP's draft number; required for {@link Finding#CREATED}, absent otherwise
     * @return the request's state after the call
     */
    DraftOrderState reconcile(SessionIdentity staff, long requestId, Finding finding, String erpOrderRef);
}
