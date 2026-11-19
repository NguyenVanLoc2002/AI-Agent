package com.secureerp.agent.handoff;

import com.secureerp.agent.platform.ConversationId;

/**
 * Opens a handoff record (UC-05, DATA_FLOW §3). EP-04 minimum (decision 2026-11-19): one OPEN record per
 * conversation with its first reason code; the staff queue, claim and resolve come with EP-08. The caller
 * locks the bot for the conversation in the same flow.
 */
public interface Handoffs {

    /** Records an open handoff; when one is already open, it is kept (its first reason stays). */
    void open(ConversationId conversation, HandoffReason reason);
}
