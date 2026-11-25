package com.secureerp.agent.conversation.internal;

import com.secureerp.agent.agent.Agent;
import com.secureerp.agent.agent.AgentReply;
import com.secureerp.agent.agent.AgentTurn;
import com.secureerp.agent.catalog.VehicleContext;
import com.secureerp.agent.catalog.VinIntake;
import com.secureerp.agent.conversation.Chat;
import com.secureerp.agent.conversation.ChatTurn;
import com.secureerp.agent.handoff.HandoffReason;
import com.secureerp.agent.handoff.Handoffs;
import com.secureerp.agent.identity.AuthenticatedCustomerRef;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.identity.Team;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.platform.KillSwitch;
import com.secureerp.agent.policy.InboundSanitizer;
import com.secureerp.agent.quote.QuoteCard;
import com.secureerp.agent.quote.QuoteCards;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Chat intake and turn handling (UC-01, DATA_FLOW §1; EP-04). Identity always comes from the session
 * ({@link SessionIdentity}); the conversation from the URL is used only after the owner check, and the
 * session customer must be the one the conversation was bound to (A-35). Per message:
 * <ol>
 *   <li>owner check (404 otherwise), customer binding (403);</li>
 *   <li>inbound sanitizer (A-21) — only sanitized text is stored; a block hands off (DLP_INBOUND);</li>
 *   <li>atomic admission: dedup by client event, one turn in flight per conversation (409), limits per
 *       conversation and principal (429), conversation status read under the lock;</li>
 *   <li>a conversation waiting for staff gets a fixed reply, no model call;</li>
 *   <li>a message the sanitizer blocked hands off (DLP_INBOUND), whether the AI is on or off (A-51);</li>
 *   <li>kill switch (EP-09a, ADR-013): off or unreadable → hand off (AI_DISABLED, or PROVIDER_AUTH while the
 *       provider-auth breaker is open), nothing else runs;</li>
 *   <li>VIN intake with the raw text (A-29): Invalid/Multiple → ask again, Unavailable → hand off; no
 *       model call in either case (AC-02.1);</li>
 *   <li>agent turn with the policy-safe history up to this message (ADR-011, last
 *       {@value AgentTurn#MAX_HISTORY} customer messages); an issued quote is rendered here on the server
 *       for the bound customer (A-36c).</li>
 * </ol>
 * Every admitted turn ends with a stored reply: an unexpected failure hands off, and a turn left
 * unanswered past {@code turnTimeout} is completed as handed off when its event is replayed (EP-04 reviews,
 * security M2 / architecture M2). Runs outside a database transaction except for the short admission and
 * handoff writes (agent, tools and gateway audit write in their own).
 */
@Service
class ChatService implements Chat {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    static final int MAX_LISTED_MESSAGES = 50;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final ConversationStore store;
    private final InboundSanitizer sanitizer;
    private final VehicleContext vehicles;
    private final Agent agent;
    private final QuoteCards quoteCards;
    private final Handoffs handoffs;
    private final ConversationProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final KillSwitch killSwitch;

    ChatService(ConversationStore store, InboundSanitizer sanitizer, VehicleContext vehicles, Agent agent,
            QuoteCards quoteCards, Handoffs handoffs, ConversationProperties properties,
            TransactionTemplate transactions, Clock clock, KillSwitch killSwitch) {
        this.killSwitch = killSwitch;
        this.store = store;
        this.sanitizer = sanitizer;
        this.vehicles = vehicles;
        this.agent = agent;
        this.quoteCards = quoteCards;
        this.handoffs = handoffs;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * RBAC: only a linked customer served by a team creates a conversation (decisions 2026-11-19), bound to
     * the customer (A-35) and to that team (D-08 PoC default).
     */
    @Override
    public ConversationId create(SessionIdentity identity) {
        requireCustomerRole(identity);
        AuthenticatedCustomerRef customer =
                identity.customer().orElseThrow(() -> new ApplicationException(ErrorCode.CUSTOMER_NOT_LINKED));
        // Owner decision 2026-11-19 (A-42(e)): without a team nobody could take this customer's handoff, so
        // the conversation is not created at all (no orphaned work, no bot locked for good).
        Team team = identity.team().orElseThrow(() -> {
            log.warn("conversation.create denied code=CUSTOMER_NOT_ASSIGNED");
            return new ApplicationException(ErrorCode.CUSTOMER_NOT_ASSIGNED);
        });
        ConversationId id = store.create(identity.principal(), customer.bindingDigest(), identity.channel().name(),
                        team, properties.newConversationsPerPrincipalPerHour(),
                        clock.instant().minus(WINDOW))
                .orElseThrow(() -> new ApplicationException(ErrorCode.TOO_MANY_REQUESTS));
        log.info("conversation.created");
        return id;
    }

    @Override
    public ChatTurn post(SessionIdentity identity, ConversationId id, UUID clientEventId, String rawText) {
        ConversationStore.Conversation conversation = owned(identity, id);
        AuthenticatedCustomerRef customer = boundCustomer(identity, conversation);

        InboundSanitizer.Result sanitized = sanitizer.sanitize(rawText);
        boolean blocked = sanitized instanceof InboundSanitizer.Blocked;
        String storedText = sanitized instanceof InboundSanitizer.Sanitized clean ? clean.text()
                : ChatTexts.BLOCKED_PLACEHOLDER;
        UUID turnId = UUID.randomUUID();
        Instant now = clock.instant();
        ConversationStore.Admission admission = store.admit(identity.principal(), id, turnId, clientEventId,
                storedText, blocked, new ConversationStore.TurnLimits(properties.messagesPerConversationPerHour(),
                        properties.messagesPerPrincipalPerHour(), now.minus(WINDOW),
                        now.minus(properties.turnTimeout())));
        return switch (admission) {
            case ConversationStore.Admission.Duplicate duplicate -> replay(identity, id, duplicate.turn());
            case ConversationStore.Admission.InProgress inProgress -> throw new ApplicationException(ErrorCode.CONFLICT);
            case ConversationStore.Admission.RateLimited limited ->
                    throw new ApplicationException(ErrorCode.TOO_MANY_REQUESTS);
            case ConversationStore.Admission.Admitted admitted -> {
                try {
                    yield run(identity, id, customer, turnId, admitted, rawText, sanitized);
                } catch (RuntimeException unexpected) {
                    // Fail closed with a stored reply, so the turn is complete and a replay gets the same answer.
                    log.error("conversation.turn failed exception={}", unexpected.getClass().getName());
                    try {
                        yield handOff(identity, id, turnId, HandoffReason.PROVIDER_ERROR);
                    } catch (RuntimeException notHandedOff) {
                        // The handoff itself could not be recorded (e.g. its lock was busy): a safe error, not a
                        // 500. The turn has no reply yet; replaying the event after the turn timeout completes it.
                        log.error("conversation.turn handoff_failed exception={}", notHandedOff.getClass().getName());
                        throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
                    }
                }
            }
        };
    }

    private ChatTurn run(SessionIdentity identity, ConversationId id, AuthenticatedCustomerRef customer, UUID turnId,
            ConversationStore.Admission.Admitted admitted, String rawText, InboundSanitizer.Result sanitized) {
        if (admitted.status() == ConversationStore.Status.HANDOFF_PENDING) {
            return reply(id, turnId, ChatTexts.WAITING_FOR_STAFF, true, null);
        }
        if (admitted.status() == ConversationStore.Status.HUMAN_ACTIVE) {
            // EP-08a: the message is stored for the staff member; the bot stays silent (no model call).
            return reply(id, turnId, ChatTexts.WITH_STAFF, true, null);
        }
        // DLP-OFF (A-51, owner 2026-11-23): a message the inbound sanitizer blocked hands off as DLP_INBOUND
        // whether the AI is on or off, so staff keep the DLP signal. It runs nothing either way (no VIN intake,
        // no agent, no tool, no provider call), so checking it first gives the kill switch nothing to guard.
        if (sanitized instanceof InboundSanitizer.Blocked block) {
            log.warn("conversation.turn inbound_blocked code={}", block.policyCode());
            return handOff(identity, id, turnId, HandoffReason.DLP_INBOUND);
        }
        // DATA_FLOW step 2 (EP-09a, ADR-013): with the AI off nothing below runs for this turn (no VIN intake,
        // no agent, no tool); the conversation goes to staff. The agent checks again before tools and the
        // gateway before every provider call.
        KillSwitch.State ai = aiState();
        if (!ai.aiEnabled()) {
            log.warn("conversation.turn ai_disabled reason={}", ai.disabledReason());
            return handOff(identity, id, turnId, ai.disabledReason() == KillSwitch.Reason.PROVIDER_AUTH
                    ? HandoffReason.PROVIDER_AUTH : HandoffReason.AI_DISABLED);
        }

        String maskedVin = null;
        switch (vehicles.register(id, rawText)) {
            case VinIntake.None none -> { }
            case VinIntake.Accepted accepted -> maskedVin = accepted.maskedVin();
            case VinIntake.Invalid invalid -> {
                return reply(id, turnId, ChatTexts.VIN_AGAIN, false, null);
            }
            case VinIntake.Multiple multiple -> {
                return reply(id, turnId, ChatTexts.VIN_AGAIN, false, null);
            }
            case VinIntake.Unavailable unavailable -> {
                return handOff(identity, id, turnId, HandoffReason.CATALOG_UNAVAILABLE);
            }
        }

        List<String> history = store.customerTextsUpTo(id, admitted.messageId(), AgentTurn.MAX_HISTORY);
        AgentReply answer = agent.reply(new AgentTurn(identity.principal(), identity.role(), identity.channel(), id,
                turnId.toString(), customer, history, maskedVin));
        return switch (answer) {
            case AgentReply.Handoff handoff -> handOff(identity, id, turnId, reason(handoff.reason()));
            case AgentReply.Answer text -> {
                if (text.issuedQuoteHandle().isEmpty()) {
                    yield reply(id, turnId, text.text(), false, null);
                }
                Optional<QuoteCard> card = quoteCards.render(text.issuedQuoteHandle().get(), id, customer);
                if (card.isEmpty()) {
                    // The answer talks about a quote the customer cannot see: hand off instead (fail closed).
                    log.warn("conversation.turn quote_card_not_rendered");
                    yield handOff(identity, id, turnId, HandoffReason.QUOTE_NOT_ISSUED);
                }
                yield reply(id, turnId, text.text(), false, card.get());
            }
        };
    }

    @Override
    public List<Chat.ChatMessage> messages(SessionIdentity identity, ConversationId id) {
        ConversationStore.Conversation conversation = owned(identity, id);
        boundCustomer(identity, conversation);
        return store.messages(id, MAX_LISTED_MESSAGES).stream()
                .map(ChatService::view)
                .toList();
    }

    /** One view of a stored message for the customer and the staff reads (A-61: UTC, whole seconds). */
    static Chat.ChatMessage view(ConversationStore.StoredMessage message) {
        return new Chat.ChatMessage(message.direction().name(), message.text(),
                message.sentAt().truncatedTo(ChronoUnit.SECONDS));
    }

    private ConversationStore.Conversation owned(SessionIdentity identity, ConversationId id) {
        requireCustomerRole(identity);
        // Another principal's conversation and a missing one look the same (RBAC_MATRIX §3: 404).
        return store.findOwned(identity.principal(), id).orElseThrow(() -> {
            log.warn("conversation.access denied code=FORBIDDEN_RESOURCE");
            return new ApplicationException(ErrorCode.NOT_FOUND);
        });
    }

    /** A-35: the session customer must be the customer the conversation was bound to. */
    private static AuthenticatedCustomerRef boundCustomer(SessionIdentity identity,
            ConversationStore.Conversation conversation) {
        return identity.customer()
                .filter(customer -> customer.bindingDigest().equals(conversation.customerDigest()))
                .orElseThrow(() -> {
                    log.warn("conversation.access denied code=CUSTOMER_NOT_BOUND");
                    return new ApplicationException(ErrorCode.CUSTOMER_NOT_LINKED);
                });
    }

    /** RBAC_MATRIX: only CUSTOMER chats with the bot; SALES writes only while HUMAN_ACTIVE (EP-08). */
    private static void requireCustomerRole(SessionIdentity identity) {
        if (identity.role() != Role.CUST) {
            throw new ApplicationException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * A repeated client event returns the stored reply (no card: the card is rendered per response). A
     * turn still running gets 409; one left unanswered past {@code turnTimeout} is completed as handed off.
     */
    private ChatTurn replay(SessionIdentity identity, ConversationId id, ConversationStore.PreviousTurn turn) {
        if (turn.botText().isPresent()) {
            return new ChatTurn(turn.turnId(), turn.botText().get(), turn.handedOff(), true, null);
        }
        if (turn.startedAt().isAfter(clock.instant().minus(properties.turnTimeout()))) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        log.warn("conversation.turn abandoned_turn_completed");
        ChatTurn completed = handOff(identity, id, turn.turnId(), HandoffReason.PROVIDER_ERROR);
        return new ChatTurn(completed.turnId(), completed.reply(), true, true, null);
    }

    /** Fail closed like the gateway: a switch that throws (against its contract) is off (reviews L2). */
    private KillSwitch.State aiState() {
        try {
            return Objects.requireNonNull(killSwitch.current(), "state");
        } catch (RuntimeException e) {
            return KillSwitch.State.unreadable();
        }
    }

    /** Handoff record, then the bot lock, in one transaction (architecture review M3), then the stored reply. */
    private ChatTurn handOff(SessionIdentity identity, ConversationId id, UUID turnId, HandoffReason reason) {
        transactions.executeWithoutResult(tx -> {
            // The handoff first: it takes the conversation's handoff lock, so a concurrent resolve is over
            // before the bot is locked here (review 2026-11-19).
            // A conversation created before teams existed takes the team serving its customer now.
            handoffs.open(id, reason, store.teamOf(id).or(identity::team).orElse(null));
            store.markHandoffPending(identity.principal(), id);
        });
        return reply(id, turnId, ChatTexts.HANDED_OFF, true, null);
    }

    private ChatTurn reply(ConversationId id, UUID turnId, String text, boolean handedOff, QuoteCard card) {
        store.insertBot(id, turnId, text, handedOff);
        return new ChatTurn(turnId, text, handedOff, false, card);
    }

    static HandoffReason reason(AgentReply.Reason reason) {
        return switch (reason) {
            case DLP_EGRESS -> HandoffReason.DLP_EGRESS;
            case PROVIDER_ERROR -> HandoffReason.PROVIDER_ERROR;
            case PROVIDER_AUTH -> HandoffReason.PROVIDER_AUTH;
            case AI_DISABLED -> HandoffReason.AI_DISABLED;
            case CATALOG_UNAVAILABLE -> HandoffReason.CATALOG_UNAVAILABLE;
            case TOOL_REJECTED_REPEATED -> HandoffReason.TOOL_REJECTED_REPEATED;
            case QUOTE_NOT_ISSUED -> HandoffReason.QUOTE_NOT_ISSUED;
            case CUSTOMER_REQUEST -> HandoffReason.CUSTOMER_REQUEST;
            case UNSURE -> HandoffReason.UNSURE;
            case OUT_OF_SCOPE -> HandoffReason.OUT_OF_SCOPE;
            case OUTPUT_REJECTED -> HandoffReason.OUTPUT_REJECTED;
            case AGENT_LIMIT -> HandoffReason.AGENT_LIMIT;
        };
    }
}
