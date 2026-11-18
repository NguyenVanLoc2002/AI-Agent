package com.secureerp.agent.catalog.internal;

import com.secureerp.agent.catalog.spi.Vin;
import com.secureerp.agent.platform.ConversationId;
import java.time.Instant;
import java.util.Optional;

/** One current VIN per conversation, with an expiry (EP-05, owner decision 2026-11-18). */
interface ConversationVinStore {

    /** Stores or replaces the conversation's current VIN. */
    void put(ConversationId conversation, Vin vin, Instant registeredAt, Instant expiresAt);

    /** Removes the conversation's current VIN, if any. */
    void clear(ConversationId conversation);

    /** Deletes every expired row (bounds how long a full VIN is kept; retention D-09 still OPEN). */
    void purgeExpired(Instant now);

    /** The current VIN if it has not expired at {@code now}. */
    Optional<Vin> current(ConversationId conversation, Instant now);
}
