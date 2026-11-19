package com.secureerp.agent.quote;

import com.secureerp.agent.identity.SessionIdentity;
import java.util.Objects;
import java.util.Optional;

/**
 * The customer's explicit confirmation of one quote version (UC-04, DATA_FLOW §2; ADR-007, ADR-009).
 * Only an authenticated customer can call it, for a quote of their own, with the quote reference,
 * the version and the confirmation token of the server-rendered card; a chat message never confirms
 * anything (BR-14) and the agent has no way to call this (BR-20).
 *
 * <p>Failures are {@code ApplicationException} codes: 403 (not a linked customer), 404 (unknown
 * reference, someone else's quote, unknown version or wrong token: all the same answer), 409
 * {@code QUOTE_STALE_VERSION}, 409 {@code QUOTE_REPRICED} (a new version was issued and needs a fresh
 * confirmation), 410 {@code QUOTE_EXPIRED}, 429, 503 (the ERP could not be asked; handed to staff).
 */
public interface QuoteConfirmations {

    /** Where a confirmed or refused quote version stands. Repeating the call returns the same answer. */
    enum State {
        DRAFT_ORDER_CREATED(false),
        /** Another request is sending the order, or its answer is not recorded yet. */
        ORDER_SUBMITTING(false),
        /** The ERP outcome is unknown; staff reconcile it. Nothing is sent again. */
        ORDER_OUTCOME_UNKNOWN(true),
        ORDER_FAILED(true),
        /** The re-check did not pass; staff take over. The reason is internal. */
        CONFIRMATION_BLOCKED(true);

        private final boolean handedOff;

        State(boolean handedOff) {
            this.handedOff = handedOff;
        }

        public boolean handedOff() {
            return handedOff;
        }
    }

    record Confirmation(String quoteRef, int version, State state) {

        public Confirmation {
            Objects.requireNonNull(quoteRef, "quoteRef");
            Objects.requireNonNull(state, "state");
        }
    }

    /**
     * The customer's own quote as it stands now: its latest version and status, with the card (prices
     * and a fresh confirmation token) only while it can still be confirmed.
     */
    record Overview(String quoteRef, int version, String status, QuoteCard quoteCard) {

        public Overview {
            Objects.requireNonNull(quoteRef, "quoteRef");
            Objects.requireNonNull(status, "status");
        }

        public Optional<QuoteCard> card() {
            return Optional.ofNullable(quoteCard);
        }

        @Override
        public String toString() {
            return "Overview[" + status + ", card=" + (quoteCard != null) + "]";
        }
    }

    Confirmation confirm(SessionIdentity identity, String quoteRef, int version, String confirmationToken);

    Overview current(SessionIdentity identity, String quoteRef);
}
