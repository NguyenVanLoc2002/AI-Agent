package com.secureerp.agent.conversation.internal;

import com.secureerp.agent.audit.AuditEvent;
import com.secureerp.agent.audit.AuditTrail;
import com.secureerp.agent.audit.DeniedInTransaction;
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
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
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
    private final AuditTrail audit;

    StaffChatService(ConversationStore store, Handoffs handoffs, TransactionTemplate transactions,
            StaffQuotes quotes, AuditTrail audit) {
        this.store = store;
        this.handoffs = handoffs;
        this.transactions = transactions;
        this.quotes = quotes;
        this.audit = audit;
    }

    @Override
    public List<Chat.ChatMessage> messages(SessionIdentity staff, ConversationId conversation) {
        requireHolder(staff, conversation);
        // MSG-READ-LOCK (A-53, owner 2026-11-23): checked again and read under the conversation's handoff lock, as
        // the quote read and the post do, so a resolve cannot land between the check and the read (A-15).
        List<Chat.ChatMessage> messages;
        try {
            messages = transactions.execute(tx -> {
                requireStillHolding(staff, conversation);
                return store.messages(conversation, ChatService.MAX_LISTED_MESSAGES).stream()
                        .map(ChatService::view)
                        .toList();
            });
        } catch (DeniedInTransaction lostTheHandoff) {
            throw denied(lostTheHandoff);
        } catch (PessimisticLockingFailureException busy) {
            log.warn("conversation.staff_messages refused code=CONFLICT");
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        // AUDIT-READS (A-52, owner 2A): best effort, collapsed per minute; never which conversation or any text.
        // Recorded after the read transaction returned its connection (EP-09b reviews M-1).
        audit.recordSeparately(new AuditEvent(AuditEvent.Action.STAFF_CONVERSATION_READ,
                AuditEvent.Resource.CONVERSATION, "READ", staff.auditActor()));
        return Objects.requireNonNull(messages);
    }

    @Override
    public void post(SessionIdentity staff, ConversationId conversation, UUID clientEventId, String text) {
        requireHolder(staff, conversation);
        if (clientEventId == null || text == null || text.isBlank() || text.length() > MAX_TEXT) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        // Checked again and written under the conversation's handoff lock: the text cannot land after the
        // handoff was resolved, or in the session of a member who claimed it next.
        ConversationStore.StaffInsert outcome;
        try {
            // EP-09b (A-42(j)): the audit record commits with the message or not at all (decision 2A).
            outcome = transactions.execute(tx -> {
                requireStillHolding(staff, conversation);
                ConversationStore.StaffInsert inserted = store.insertStaff(conversation, clientEventId, text);
                if (inserted == ConversationStore.StaffInsert.STORED) {
                    audit.record(new AuditEvent(AuditEvent.Action.STAFF_MESSAGE_POSTED,
                            AuditEvent.Resource.CONVERSATION, "STORED", staff.auditActor()));
                }
                return inserted;
            });
        } catch (DeniedInTransaction lostTheHandoff) {
            throw denied(lostTheHandoff);
        } catch (PessimisticLockingFailureException busy) {
            // Another change of this conversation's handoff held the lock too long: nothing was written.
            log.warn("conversation.staff_message refused code=CONFLICT");
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        switch (Objects.requireNonNull(outcome)) {
            case STORED -> log.info("conversation.staff_message stored");
            // STAFF-MSG-ID (A-90, owner 2A): the resend answers like the first send.
            case DUPLICATE -> log.info("conversation.staff_message duplicate");
            case ID_REUSED -> {
                log.warn("conversation.staff_message refused code=CONFLICT reason=EVENT_ID_REUSED");
                throw new ApplicationException(ErrorCode.CONFLICT);
            }
            case NOT_ACTIVE -> throw new ApplicationException(ErrorCode.NOT_FOUND);
        }
    }

    @Override
    public StaffQuotes.StaffQuoteView quote(SessionIdentity staff, ConversationId conversation) {
        requireHolder(staff, conversation);
        // EP-08b reviews (security L-6, backend L-3): checked again and read under the conversation's handoff
        // lock, so a resolve cannot land between the check and the read of the prices (A-15).
        // AUDIT-STRICT-READ (A-45(y), owner 2026-11-20): the read of customer prices is recorded inside the read
        // transaction, under the handoff lock; no record, no prices (fail closed, 503).
        Optional<StaffQuotes.StaffQuoteView> quote;
        try {
            quote = transactions.execute(tx -> {
                requireStillHolding(staff, conversation);
                Optional<StaffQuotes.StaffQuoteView> found = quotes.newestOf(conversation);
                if (found.isPresent()) {
                    audit.record(new AuditEvent(AuditEvent.Action.STAFF_QUOTE_READ, AuditEvent.Resource.QUOTE,
                            "READ", staff.auditActor()));
                }
                return found;
            });
        } catch (DeniedInTransaction lostTheHandoff) {
            throw denied(lostTheHandoff);
        } catch (PessimisticLockingFailureException busy) {
            log.warn("conversation.staff_quote refused code=CONFLICT");
            throw new ApplicationException(ErrorCode.CONFLICT);
        } catch (DataAccessException | TransactionException notRecorded) {
            log.error("conversation.staff_quote refused code=SERVICE_UNAVAILABLE exception={}",
                    notRecorded.getClass().getSimpleName());
            throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        StaffQuotes.StaffQuoteView view = Objects.requireNonNull(quote).orElseThrow(() -> {
            log.info("conversation.staff_quote outcome=NOT_FOUND");
            return new ApplicationException(ErrorCode.NOT_FOUND);
        });
        log.info("conversation.staff_quote outcome=READ");
        return view;
    }

    /**
     * The handoff was resolved or taken over between {@link #requireHolder} and the lock (round-2 review L-2): a
     * denial like the first check, recorded after the transaction ({@link #denied}).
     */
    private void requireStillHolding(SessionIdentity staff, ConversationId conversation) {
        if (!handoffs.holdFor(conversation, staff.principal())) {
            log.warn("conversation.staff_access denied code=FORBIDDEN_RESOURCE");
            throw new DeniedInTransaction(AuditEvent.denied(AuditEvent.Resource.CONVERSATION,
                    AuditEvent.FORBIDDEN_RESOURCE, staff.auditActor()));
        }
    }

    private ApplicationException denied(DeniedInTransaction denial) {
        audit.recordSeparately(denial.event());
        return new ApplicationException(ErrorCode.NOT_FOUND);
    }

    private void requireHolder(SessionIdentity staff, ConversationId conversation) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(conversation, "conversation");
        if (staff.role() != Role.SALES) {
            audit.recordSeparately(AuditEvent.denied(AuditEvent.Resource.CONVERSATION, AuditEvent.FORBIDDEN,
                    staff.auditActor()));
            throw new ApplicationException(ErrorCode.FORBIDDEN);
        }
        if (!handoffs.heldBy(conversation, staff.principal())) {
            log.warn("conversation.staff_access denied code=FORBIDDEN_RESOURCE");
            audit.recordSeparately(AuditEvent.denied(AuditEvent.Resource.CONVERSATION, AuditEvent.FORBIDDEN_RESOURCE,
                    staff.auditActor()));
            throw new ApplicationException(ErrorCode.NOT_FOUND);
        }
    }
}
