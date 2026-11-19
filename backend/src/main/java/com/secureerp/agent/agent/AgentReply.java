package com.secureerp.agent.agent;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of one agent turn. An {@link Answer} carries model text that passed the OutputValidator and,
 * when a quote was issued by the {@code requestQuote} tool in this turn, its handle (taken from the
 * tool result, never from model text) so the caller renders the card on the server. A {@link Handoff}
 * carries only an internal reason; the customer gets a fixed text.
 */
public sealed interface AgentReply {

    record Answer(String text, String quoteHandle) implements AgentReply {

        public Answer {
            Objects.requireNonNull(text, "text");
        }

        public Optional<String> issuedQuoteHandle() {
            return Optional.ofNullable(quoteHandle);
        }

        @Override
        public String toString() {
            return "Answer[chars=" + text.length() + ", quote=" + (quoteHandle != null) + "]";
        }
    }

    record Handoff(Reason reason) implements AgentReply {

        public Handoff {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Internal reason codes (THREAT_MODEL §5 plus EP-04 additions); mapped to handoff reasons by the caller. */
    enum Reason {
        DLP_EGRESS,
        PROVIDER_ERROR,
        PROVIDER_AUTH,
        CATALOG_UNAVAILABLE,
        TOOL_REJECTED_REPEATED,
        QUOTE_NOT_ISSUED,
        CUSTOMER_REQUEST,
        /** The model's final text failed the OutputValidator (decision 2026-11-19: drop it and hand off). */
        OUTPUT_REJECTED,
        /** No final answer within the model-call limit of one turn (decision 2026-11-19: 3 calls). */
        AGENT_LIMIT
    }
}
