package com.secureerp.agent.order;

import com.secureerp.agent.identity.SessionIdentity;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The staff decision on a created draft order (EP-08b; ADR-007 STAFF_APPROVED / STAFF_REJECTED; A-15:
 * the approval happens in the ERP and SALES record it by hand, no read-back, D-01). Owner decisions
 * 2026-11-20 (A-43): SALES of the customer's team, the same scope as reconciliation; the decision is
 * final, has no free text, and only changes the quote's status (no message to the customer).
 *
 * <p>Failures are {@code ApplicationException} codes: 403 for anyone but SALES, 404 for another team's
 * request or a missing one (same answer), 409 for a request that is not waiting for a decision, the
 * opposite decision on a decided one, or a quote that cannot follow. The same decision again answers
 * the current state.
 */
public interface StaffDecisions {

    enum Decision {
        APPROVED,
        REJECTED
    }

    /** A created draft waiting for the decision. No customer, no prices. */
    record AwaitingDecision(long requestId, String quoteRef, int quoteVersion, String erpOrderRef, Instant since) {

        public AwaitingDecision {
            Objects.requireNonNull(quoteRef, "quoteRef");
            Objects.requireNonNull(erpOrderRef, "erpOrderRef");
        }
    }

    /** The team's created drafts without a decision, oldest first (at most 50). */
    List<AwaitingDecision> awaiting(SessionIdentity staff);

    /** @return the request's state after the call: {@code STAFF_APPROVED} or {@code STAFF_REJECTED} */
    DraftOrderState decide(SessionIdentity staff, long requestId, Decision decision);
}
