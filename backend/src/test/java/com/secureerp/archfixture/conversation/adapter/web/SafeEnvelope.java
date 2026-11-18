package com.secureerp.archfixture.conversation.adapter.web;

import java.util.List;

/** Compliant nested request body: technical content only. */
public record SafeEnvelope(String text, List<Line> lines) {

    public record Line(String sku, int quantity) {
    }
}
