package com.secureerp.agent.quote.internal;

import com.secureerp.agent.order.DraftOrderState;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Quote states and the only transitions allowed between them (ADR-007 ACCEPTED; SYSTEM_ARCHITECTURE
 * §7). {@code REJECTED} is the staff rejection of the draft order (BRD "HUMAN_REJECTED"); the staff
 * decision is recorded on the draft order request and mirrored here (EP-08b). Every status write checks {@link #canBecome} and is a conditional update
 * on the expected current status.
 */
enum QuoteStatus {
    DRAFT,
    ISSUED,
    CONFIRMED,
    CONFIRMATION_BLOCKED,
    ORDER_SUBMITTING,
    ORDER_OUTCOME_UNKNOWN,
    ORDER_FAILED,
    DRAFT_ORDER_CREATED,
    STAFF_APPROVED,
    REJECTED,
    EXPIRED,
    SUPERSEDED,
    CANCELLED;

    boolean canBecome(QuoteStatus next) {
        return switch (this) {
            case DRAFT -> next == ISSUED;
            case ISSUED -> Set.of(CONFIRMED, CONFIRMATION_BLOCKED, EXPIRED, SUPERSEDED, CANCELLED).contains(next);
            case CONFIRMED -> next == ORDER_SUBMITTING;
            case ORDER_SUBMITTING -> Set.of(DRAFT_ORDER_CREATED, ORDER_OUTCOME_UNKNOWN, ORDER_FAILED).contains(next);
            case ORDER_OUTCOME_UNKNOWN -> Set.of(DRAFT_ORDER_CREATED, ORDER_FAILED).contains(next);
            case DRAFT_ORDER_CREATED -> Set.of(STAFF_APPROVED, REJECTED).contains(next);
            case CONFIRMATION_BLOCKED, ORDER_FAILED, STAFF_APPROVED, REJECTED, EXPIRED, SUPERSEDED, CANCELLED -> false;
        };
    }

    /** The states that may become {@code next}. */
    static List<QuoteStatus> sourcesOf(QuoteStatus next) {
        return Arrays.stream(values()).filter(status -> status.canBecome(next)).toList();
    }

    /** ADR-007: the quote mirrors the draft order request, which is the source of truth for the ERP outcome. */
    static QuoteStatus mirrorOf(DraftOrderState state) {
        return switch (state) {
            case PENDING, SUBMITTED -> ORDER_SUBMITTING;
            case CREATED, RECONCILED_CREATED -> DRAFT_ORDER_CREATED;
            case REJECTED, RECONCILED_ABSENT -> ORDER_FAILED;
            case OUTCOME_UNKNOWN -> ORDER_OUTCOME_UNKNOWN;
            // EP-08b: the staff decision recorded by SALES (A-15, A-43).
            case STAFF_APPROVED -> STAFF_APPROVED;
            case STAFF_REJECTED -> REJECTED;
        };
    }
}
