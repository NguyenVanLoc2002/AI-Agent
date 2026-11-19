package com.secureerp.agent.conversation;

import com.secureerp.agent.quote.QuoteCard;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Result of one customer message: the reply text, whether the conversation is with staff, whether this
 * was a repeated client event (the stored reply is returned, without a card), and the quote card
 * rendered on the server for this customer (RESTRICTED; ARCH-13a).
 */
public record ChatTurn(UUID turnId, String reply, boolean handedOff, boolean duplicate, QuoteCard quoteCard) {

    public ChatTurn {
        Objects.requireNonNull(turnId, "turnId");
        Objects.requireNonNull(reply, "reply");
    }

    public Optional<QuoteCard> card() {
        return Optional.ofNullable(quoteCard);
    }

    @Override
    public String toString() {
        return "ChatTurn[handedOff=" + handedOff + ", duplicate=" + duplicate + ", card=" + (quoteCard != null) + "]";
    }
}
