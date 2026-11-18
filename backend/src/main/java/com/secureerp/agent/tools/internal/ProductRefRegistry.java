package com.secureerp.agent.tools.internal;

import com.secureerp.agent.catalog.spi.Provenance;
import com.secureerp.agent.platform.ConversationId;
import java.time.Instant;
import java.util.Optional;

/**
 * Server-side registry of issued {@code productRef} tokens (A-26, APPROVED for productRef
 * 2026-11-18; ADR-008 "random + stored"). A token is random, bound to the conversation whose search
 * produced it, and expires. A productRef the model sends back is honoured only if this registry
 * resolves it for the same conversation (T-TOOL-04).
 */
interface ProductRefRegistry {

    record IssuedProductRef(String token, String sku, Provenance provenance) {
    }

    String issue(ConversationId conversation, String sku, Provenance provenance, Instant now);

    Optional<IssuedProductRef> resolve(ConversationId conversation, String token, Instant now);
}
