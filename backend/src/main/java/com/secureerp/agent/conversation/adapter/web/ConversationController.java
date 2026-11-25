package com.secureerp.agent.conversation.adapter.web;

import com.secureerp.agent.conversation.Chat;
import com.secureerp.agent.conversation.ChatTurn;
import com.secureerp.agent.identity.CurrentIdentity;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.platform.Money;
import com.secureerp.agent.quote.QuoteCard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web chat demo (DATA_FLOW §1 step 1, UC-01). Registered only with profile {@code demo} (ADR-015
 * ACCEPTED 2026-11-19). Transport only: the identity comes from the session ({@link CurrentIdentity}),
 * never from the body, a header or the URL (ARCH-08b); the conversation in the URL is checked against the
 * session principal and bound customer by the service (404 for anyone else's). The body size is capped
 * before parsing ({@link RequestSizeFilter}); unknown JSON fields are rejected (application.yml).
 */
@RestController
@Profile("demo")
@RequestMapping(path = "/api/demo/conversations", produces = MediaType.APPLICATION_JSON_VALUE)
class ConversationController {

    private final CurrentIdentity identity;
    private final Chat chat;

    ConversationController(CurrentIdentity identity, Chat chat) {
        this.identity = identity;
        this.chat = chat;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ConversationView create() {
        return new ConversationView(chat.create(session()).value());
    }

    @PostMapping(path = "/{conversationId}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    TurnView post(@PathVariable String conversationId, @Valid @RequestBody MessageRequest request) {
        ChatTurn turn = chat.post(session(), conversation(conversationId), request.clientEventId(), request.text());
        return new TurnView(turn.turnId().toString(), turn.reply(), turn.handedOff(), turn.duplicate(),
                turn.card().map(QuoteCardView::of).orElse(null));
    }

    /** UX13-CONV-STATE (A-62): the state of the session customer's own conversation; 404 for any other. */
    @GetMapping("/{conversationId}")
    ConversationStateView state(@PathVariable String conversationId) {
        return new ConversationStateView(chat.state(session(), conversation(conversationId)));
    }

    @GetMapping("/{conversationId}/messages")
    List<Chat.ChatMessage> messages(@PathVariable String conversationId) {
        return chat.messages(session(), conversation(conversationId));
    }

    private SessionIdentity session() {
        return identity.current().orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    /** A malformed ID is the same 404 as someone else's conversation (no existence oracle). */
    private static ConversationId conversation(String value) {
        try {
            return new ConversationId(value);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(ErrorCode.NOT_FOUND);
        }
    }

    /** The text limit is the sanitized segment limit of the model request (SafeMessage, 2000 characters). */
    record MessageRequest(@NotNull UUID clientEventId, @NotBlank @Size(max = 2000) String text) {
    }

    record ConversationView(String conversationId) {
    }

    /** Only the customer-visible state: no reason, team, staff member or internal status (A-62, A-14). */
    record ConversationStateView(Chat.CustomerState state) {
    }

    record TurnView(String turnId, String reply, boolean handedOff, boolean duplicate, QuoteCardView quoteCard) {
    }

    /**
     * Server-rendered quote for the session customer only (ARCH-13a/13b; amounts as exact strings).
     * {@code confirmationToken} belongs to the confirm control of this card (EP-07, UC-04).
     */
    record QuoteCardView(String quoteRef, int version, List<LineView> lines, String total, String currency,
            String validUntil, List<String> notes, String confirmationToken) {

        static QuoteCardView of(QuoteCard card) {
            return new QuoteCardView(card.quoteRef(), card.version(),
                    card.lines().stream().map(line -> new LineView(line.skuCode(), line.quantity(),
                            amount(line.unitPrice()), amount(line.lineTotal()))).toList(),
                    amount(card.total()), card.total().currency().getCurrencyCode(), card.validUntil().toString(),
                    card.notes(), card.confirmationToken());
        }

        private static String amount(Money money) {
            return money.amount().toPlainString();
        }
    }

    record LineView(String sku, int quantity, String unitPrice, String lineTotal) {
    }
}
