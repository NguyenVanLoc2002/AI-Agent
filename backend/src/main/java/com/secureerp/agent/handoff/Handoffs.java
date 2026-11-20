package com.secureerp.agent.handoff;

import com.secureerp.agent.identity.PrincipalId;
import com.secureerp.agent.identity.Team;
import com.secureerp.agent.platform.ConversationId;

/**
 * Opens a handoff record (UC-05, DATA_FLOW §3) and answers who holds it. One handoff that is not
 * resolved per conversation, with its first reason code; the caller locks the bot for the
 * conversation in the same transaction. The staff side (queue, claim, resolve) is {@link HandoffInbox}.
 */
public interface Handoffs {

    /**
     * Records an open handoff for the team that serves the customer; when one is already open or
     * claimed, it is kept (its first reason and its team stay). A reason that is new to staff, that is a
     * different one, or any reason raised after the handoff was claimed, is remembered as its follow-up
     * (the first only): resolving then opens a new handoff with that reason instead of unlocking the bot.
     * Takes the conversation's handoff lock for the caller's transaction, so opening, claiming and
     * resolving are serialised per conversation: call it first, then lock the bot, in one transaction.
     *
     * @param team {@code null} only for a conversation created before teams existed (a new conversation
     *             needs one, A-42(e)): the handoff is then visible to no staff member (fail closed)
     */
    void open(ConversationId conversation, HandoffReason reason, Team team);

    /**
     * Whether this staff member holds the conversation's handoff right now: claimed by them and not
     * resolved. This is the only thing that gives staff access to a conversation's content
     * (RBAC_MATRIX; A-15: access ends at resolve).
     */
    boolean heldBy(ConversationId conversation, PrincipalId staff);

    /**
     * {@link #heldBy} under the conversation's handoff lock, held until the caller's transaction ends:
     * a write made in that transaction cannot land after the handoff was resolved or taken by someone
     * else.
     */
    boolean holdFor(ConversationId conversation, PrincipalId staff);
}
