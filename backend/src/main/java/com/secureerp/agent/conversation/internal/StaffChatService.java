package com.secureerp.agent.conversation.internal;

import com.secureerp.agent.conversation.Chat;
import com.secureerp.agent.conversation.StaffChat;
import com.secureerp.agent.handoff.Handoffs;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.platform.ErrorCode;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Staff access to a conversation's content (EP-08a). The only key is the handoff: the caller must be
 * SALES and hold the conversation's handoff right now ({@link Handoffs#heldBy}), checked on every call,
 * so access ends the moment the handoff is resolved (A-15). Message texts are never logged.
 */
@Service
class StaffChatService implements StaffChat {

    private static final Logger log = LoggerFactory.getLogger(StaffChatService.class);
    static final int MAX_TEXT = 2000;

    private final ConversationStore store;
    private final Handoffs handoffs;
    private final TransactionTemplate transactions;

    StaffChatService(ConversationStore store, Handoffs handoffs, TransactionTemplate transactions) {
        this.store = store;
        this.handoffs = handoffs;
        this.transactions = transactions;
    }

    @Override
    public List<Chat.ChatMessage> messages(SessionIdentity staff, ConversationId conversation) {
        requireHolder(staff, conversation);
        return store.messages(conversation, ChatService.MAX_LISTED_MESSAGES).stream()
                .map(message -> new Chat.ChatMessage(message.direction().name(), message.text()))
                .toList();
    }

    @Override
    public void post(SessionIdentity staff, ConversationId conversation, String text) {
        requireHolder(staff, conversation);
        if (text == null || text.isBlank() || text.length() > MAX_TEXT) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        // Checked again and written under the conversation's handoff lock: the text cannot land after the
        // handoff was resolved, or in the session of a member who claimed it next.
        boolean stored;
        try {
            stored = Boolean.TRUE.equals(transactions.execute(tx ->
                    handoffs.holdFor(conversation, staff.principal()) && store.insertStaff(conversation, text)));
        } catch (PessimisticLockingFailureException busy) {
            // Another change of this conversation's handoff held the lock too long: nothing was written.
            log.warn("conversation.staff_message refused code=CONFLICT");
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        if (!stored) {
            throw new ApplicationException(ErrorCode.NOT_FOUND);
        }
        log.info("conversation.staff_message stored");
    }

    private void requireHolder(SessionIdentity staff, ConversationId conversation) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(conversation, "conversation");
        if (staff.role() != Role.SALES) {
            throw new ApplicationException(ErrorCode.FORBIDDEN);
        }
        if (!handoffs.heldBy(conversation, staff.principal())) {
            log.warn("conversation.staff_access denied code=FORBIDDEN_RESOURCE");
            throw new ApplicationException(ErrorCode.NOT_FOUND);
        }
    }
}
