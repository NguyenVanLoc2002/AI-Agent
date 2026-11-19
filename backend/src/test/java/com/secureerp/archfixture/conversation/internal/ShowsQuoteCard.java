package com.secureerp.archfixture.conversation.internal;

import com.secureerp.archfixture.quote.QuoteCard;

/** ARCH-13a compliant: the conversation layer renders the card to the authenticated customer. */
public class ShowsQuoteCard {

    public String reference(QuoteCard card) {
        return card.quoteRef();
    }
}
