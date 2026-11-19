package com.secureerp.agent.quote;

import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.identity.AuthenticatedCustomerRef;
import com.secureerp.agent.platform.ConversationId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A request for an automatic quote (UC-03, TOOL_CONTRACTS §3.3). Built by the tool layer from
 * server-side facts only: the conversation and customer come from the authenticated session, the
 * SKUs from productRefs this conversation was issued and that were VERIFIED (BR-07, A-31).
 *
 * @param customer the session's customer; {@code null} when the session has none (unlinked
 *                 customer, D-04): the request is then NOT_QUOTED
 */
public record QuoteRequest(ConversationId conversation, AuthenticatedCustomerRef customer, List<Line> lines) {

    /** BR-10: at most 20 lines (D-10 APPROVED), quantity 1..9999 (TOOL_CONTRACTS §3.3). */
    public static final int MAX_LINES = 20;
    public static final int MAX_QUANTITY = 9999;

    public QuoteRequest {
        Objects.requireNonNull(conversation, "conversation");
        Objects.requireNonNull(lines, "lines");
        lines = List.copyOf(lines);
        if (lines.isEmpty() || lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("quote needs 1 to 20 lines");
        }
        Set<Sku> skus = new HashSet<>();
        for (Line line : lines) {
            if (!skus.add(line.sku())) {
                throw new IllegalArgumentException("duplicate sku in quote request");
            }
        }
    }

    public record Line(Sku sku, int quantity) {

        public Line {
            Objects.requireNonNull(sku, "sku");
            if (quantity < 1 || quantity > MAX_QUANTITY) {
                throw new IllegalArgumentException("quantity out of range");
            }
        }
    }
}
