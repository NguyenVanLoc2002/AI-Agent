package com.secureerp.agent.quote.internal;

import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.quote.staff.QuoteLocks;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Lifts the A-34 lock of one conversation in the caller's transaction ({@code quote_lock_lift},
 * Flyway V12; SQL in {@link JdbcQuoteStore}). Only a conversation that locks the customer right now gets
 * a row, so the table records real lifts only. Logs carry the outcome only.
 */
@Service
class DefaultQuoteLocks implements QuoteLocks {

    private static final Logger log = LoggerFactory.getLogger(DefaultQuoteLocks.class);

    private final JdbcQuoteStore store;
    private final QuoteProperties properties;
    private final Clock clock;

    DefaultQuoteLocks(JdbcQuoteStore store, QuoteProperties properties, Clock clock) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean liftAfterStaffResolved(ConversationId conversation) {
        Objects.requireNonNull(conversation, "conversation");
        Instant now = clock.instant();
        boolean lifted = store.liftConversationLock(conversation, now.minus(properties.lockAfterNotQuoted()), now);
        if (lifted) {
            log.info("quote.lock lifted");
        }
        return lifted;
    }
}
