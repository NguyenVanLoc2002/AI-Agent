package com.secureerp.archfixture.conversation.api;

import java.util.List;

/** Request body declared outside {@code adapter.web}, carrying identity two levels down. */
public record OrderEnvelope(String note, List<Line> lines) {

    public record Line(String sku, Buyer orderedBy) {
    }

    public record Buyer(String buyerId) {
    }
}
