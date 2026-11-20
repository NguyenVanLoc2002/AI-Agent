package com.secureerp.agent.conversation.internal;

import com.secureerp.agent.conversation.Chat;
import com.secureerp.agent.conversation.StaffChat;
import com.secureerp.agent.handoff.Handoffs;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.quote.staff.StaffQuotes;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    private final StaffQuotes quotes;

    StaffChatService(ConversationStore store, Handoffs handoffs, TransactionTemplate transactions,
            StaffQuotes quotes) {
        this.store = store;
        this.handoffs = handoffs;
        this.transactions = transactions;
        this.quotes = quotes;
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

    @Override
    public StaffQuotes.StaffQuoteView quote(SessionIdentity staff, ConversationId conversation) {
        requireHolder(staff, conversation);
        // EP-08b reviews (security L-6, backend L-3): checked again and read under the conversation's handoff
        // lock, so a resolve cannot land between the check and the read of the prices (A-15).
        Optional<StaffQuotes.StaffQuoteView> quote;
        try {
            quote = transactions.execute(tx -> handoffs.holdFor(conversation, staff.principal())
                    ? quotes.newestOf(conversation) : Optional.<StaffQuotes.StaffQuoteView>empty());
        } catch (PessimisticLockingFailureException busy) {
            log.warn("conversation.staff_quote refused code=CONFLICT");
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        StaffQuotes.StaffQuoteView view = Objects.requireNonNull(quote).orElseThrow(() -> {
            log.info("conversation.staff_quote outcome=NOT_FOUND");
            return new ApplicationException(ErrorCode.NOT_FOUND);
        });
        // Architecture review L-1: a central audit_event for this read of customer prices is EP-09 (A-43).
        log.info("conversation.staff_quote outcome=READ");
        return view;
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
