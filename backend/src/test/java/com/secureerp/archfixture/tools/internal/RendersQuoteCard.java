package com.secureerp.archfixture.tools.internal;

import com.secureerp.archfixture.quote.QuoteCard;

/** ARCH-13a violation: the tool layer reads the price-bearing card and could pass it to the model. */
public class RendersQuoteCard {

    public String total(QuoteCard card) {
        return Long.toString(card.totalAmount());
    }
}
