/**
 * Module {@code conversation}: chat endpoint, event dedup, conversation state, response composition.
 *
 * <p>EP-04: web chat demo (profile demo, ADR-015), conversation bound to the session customer (A-35),
 * dedup, inbound sanitizer at intake (A-21, {@code policy}), VIN intake through {@code catalog :: intake}
 * (A-29), the tool layer's {@code ConversationAccessPort} ({@code tools :: spi}), server-side quote card
 * render (ARCH-13a). Allowed module dependencies are enforced by {@code ModuleStructureTest};
 * finer-grained rules (ARCH-xx) by {@code ArchitectureRulesTest}.
 */
@ApplicationModule(displayName = "Conversation", allowedDependencies = { "agent", "handoff", "quote", "identity",
        "policy", "catalog :: intake", "tools :: spi", "platform", "audit" })
package com.secureerp.agent.conversation;

import org.springframework.modulith.ApplicationModule;
