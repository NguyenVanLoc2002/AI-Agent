package com.secureerp.agent.quote.staff;

import com.secureerp.agent.platform.ConversationId;

/**
 * The A-34 quote lock as staff release it (EP-08b, owner decision 2026-11-20, A-43). After a business
 * refusal the customer gets no automatic quote for 24 h; when staff resolve the conversation's
 * handoff without a follow-up, the refusals of that conversation no longer lock the customer.
 * Refusals of the customer's other conversations still do, and the issue limit stays.
 */
public interface QuoteLocks {

    /**
     * Runs in the caller's transaction (the resolving one), so the lift commits with the resolve or not
     * at all. The caller is responsible for having resolved the conversation's handoff.
     *
     * @return whether the conversation held a refusal that locked the customer and now no longer does
     */
    boolean liftAfterStaffResolved(ConversationId conversation);
}
