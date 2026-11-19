package com.secureerp.agent.quote.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.order.DraftOrderState;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * ADR-007 evidence: the transition table, every pair of states (valid and invalid), including the
 * failed re-check branch, and the mirror of the draft order request (SYSTEM_ARCHITECTURE §7).
 */
class QuoteStatusTest {

    /** Written by hand from ADR-007 / SYSTEM_ARCHITECTURE §7; the enum must match it exactly. */
    private static final Map<QuoteStatus, Set<QuoteStatus>> ALLOWED = Map.of(
            QuoteStatus.DRAFT, EnumSet.of(QuoteStatus.ISSUED),
            QuoteStatus.ISSUED, EnumSet.of(QuoteStatus.CONFIRMED, QuoteStatus.CONFIRMATION_BLOCKED,
                    QuoteStatus.EXPIRED, QuoteStatus.SUPERSEDED, QuoteStatus.CANCELLED),
            QuoteStatus.CONFIRMED, EnumSet.of(QuoteStatus.ORDER_SUBMITTING),
            QuoteStatus.ORDER_SUBMITTING, EnumSet.of(QuoteStatus.DRAFT_ORDER_CREATED,
                    QuoteStatus.ORDER_OUTCOME_UNKNOWN, QuoteStatus.ORDER_FAILED),
            QuoteStatus.ORDER_OUTCOME_UNKNOWN, EnumSet.of(QuoteStatus.DRAFT_ORDER_CREATED, QuoteStatus.ORDER_FAILED),
            QuoteStatus.DRAFT_ORDER_CREATED, EnumSet.of(QuoteStatus.STAFF_APPROVED, QuoteStatus.REJECTED));

    @ParameterizedTest
    @EnumSource(QuoteStatus.class)
    void everyPairFollowsTheTable(QuoteStatus from) {
        Set<QuoteStatus> allowed = ALLOWED.getOrDefault(from, EnumSet.noneOf(QuoteStatus.class));
        for (QuoteStatus to : QuoteStatus.values()) {
            assertThat(from.canBecome(to)).as("%s -> %s", from, to).isEqualTo(allowed.contains(to));
        }
    }

    @Test
    void terminalStatesAndSelfTransitionsAreRefused() {
        for (QuoteStatus terminal : EnumSet.of(QuoteStatus.CONFIRMATION_BLOCKED, QuoteStatus.ORDER_FAILED,
                QuoteStatus.STAFF_APPROVED, QuoteStatus.REJECTED, QuoteStatus.EXPIRED, QuoteStatus.SUPERSEDED,
                QuoteStatus.CANCELLED)) {
            assertThat(QuoteStatus.sourcesOf(QuoteStatus.ISSUED)).doesNotContain(terminal);
            assertThat(EnumSet.allOf(QuoteStatus.class)).noneMatch(terminal::canBecome);
        }
        for (QuoteStatus status : QuoteStatus.values()) {
            assertThat(status.canBecome(status)).as("%s -> itself", status).isFalse();
        }
    }

    @Test
    void anOrderIsNeverReachedWithoutAConfirmation() {
        assertThat(QuoteStatus.sourcesOf(QuoteStatus.ORDER_SUBMITTING)).containsExactly(QuoteStatus.CONFIRMED);
        assertThat(QuoteStatus.sourcesOf(QuoteStatus.CONFIRMED)).containsExactly(QuoteStatus.ISSUED);
        assertThat(QuoteStatus.sourcesOf(QuoteStatus.DRAFT_ORDER_CREATED))
                .containsExactly(QuoteStatus.ORDER_SUBMITTING, QuoteStatus.ORDER_OUTCOME_UNKNOWN);
    }

    @Test
    void theQuoteMirrorsTheDraftOrderRequest() {
        assertThat(QuoteStatus.mirrorOf(DraftOrderState.PENDING)).isEqualTo(QuoteStatus.ORDER_SUBMITTING);
        assertThat(QuoteStatus.mirrorOf(DraftOrderState.SUBMITTED)).isEqualTo(QuoteStatus.ORDER_SUBMITTING);
        assertThat(QuoteStatus.mirrorOf(DraftOrderState.CREATED)).isEqualTo(QuoteStatus.DRAFT_ORDER_CREATED);
        assertThat(QuoteStatus.mirrorOf(DraftOrderState.RECONCILED_CREATED)).isEqualTo(QuoteStatus.DRAFT_ORDER_CREATED);
        assertThat(QuoteStatus.mirrorOf(DraftOrderState.REJECTED)).isEqualTo(QuoteStatus.ORDER_FAILED);
        assertThat(QuoteStatus.mirrorOf(DraftOrderState.RECONCILED_ABSENT)).isEqualTo(QuoteStatus.ORDER_FAILED);
        assertThat(QuoteStatus.mirrorOf(DraftOrderState.OUTCOME_UNKNOWN)).isEqualTo(QuoteStatus.ORDER_OUTCOME_UNKNOWN);
    }

    /** An unknown outcome is never sent again: the request has no way back to PENDING or SUBMITTED. */
    @Test
    void draftOrderRequestTransitions() {
        Map<DraftOrderState, Set<DraftOrderState>> allowed = Map.of(
                DraftOrderState.PENDING, EnumSet.of(DraftOrderState.SUBMITTED, DraftOrderState.OUTCOME_UNKNOWN),
                DraftOrderState.SUBMITTED, EnumSet.of(DraftOrderState.CREATED, DraftOrderState.REJECTED,
                        DraftOrderState.OUTCOME_UNKNOWN),
                DraftOrderState.OUTCOME_UNKNOWN, EnumSet.of(DraftOrderState.RECONCILED_CREATED,
                        DraftOrderState.RECONCILED_ABSENT));
        for (DraftOrderState from : DraftOrderState.values()) {
            for (DraftOrderState to : DraftOrderState.values()) {
                assertThat(from.canBecome(to)).as("%s -> %s", from, to).isEqualTo(
                        allowed.getOrDefault(from, EnumSet.noneOf(DraftOrderState.class)).contains(to));
            }
        }
    }
}
