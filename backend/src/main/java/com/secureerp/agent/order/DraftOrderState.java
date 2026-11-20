package com.secureerp.agent.order;

import java.util.Set;

/**
 * State of one draft order request (SYSTEM_ARCHITECTURE §7, ADR-007). {@code PENDING} is the outbox
 * row before the ERP call; {@code OUTCOME_UNKNOWN} waits for reconciliation and is never retried
 * (ADR-009). A created draft waits for the staff decision ({@code STAFF_APPROVED} / {@code STAFF_REJECTED},
 * EP-08b); both are final.
 */
public enum DraftOrderState {
    PENDING,
    SUBMITTED,
    CREATED,
    REJECTED,
    OUTCOME_UNKNOWN,
    RECONCILED_CREATED,
    RECONCILED_ABSENT,
    /** EP-08b: SALES recorded that the draft was approved in the ERP (A-15: manual, no read-back). */
    STAFF_APPROVED,
    /** EP-08b: SALES recorded that the draft was rejected in the ERP. */
    STAFF_REJECTED;

    /** Every transition not listed here is refused. */
    public boolean canBecome(DraftOrderState next) {
        return switch (this) {
            // A request never sent within the submit timeout is treated like a lost answer.
            case PENDING -> Set.of(SUBMITTED, OUTCOME_UNKNOWN).contains(next);
            case SUBMITTED -> Set.of(CREATED, REJECTED, OUTCOME_UNKNOWN).contains(next);
            case OUTCOME_UNKNOWN -> Set.of(RECONCILED_CREATED, RECONCILED_ABSENT).contains(next);
            case CREATED, RECONCILED_CREATED -> Set.of(STAFF_APPROVED, STAFF_REJECTED).contains(next);
            case REJECTED, RECONCILED_ABSENT, STAFF_APPROVED, STAFF_REJECTED -> false;
        };
    }
}
