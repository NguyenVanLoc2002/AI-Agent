package com.secureerp.agent.conversation.adapter.web;

import com.secureerp.agent.conversation.Chat;
import com.secureerp.agent.conversation.StaffChat;
import com.secureerp.agent.identity.CurrentIdentity;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.platform.Money;
import com.secureerp.agent.quote.staff.StaffQuotes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
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
 * A staff member's view of a conversation they hold (SYSTEM_ARCHITECTURE §8, UC-06). Transport only:
 * identity from the session; whether the caller holds the conversation is decided by the service on
 * every call (404 otherwise). Profile {@code demo} only; the body is capped by the request size filter.
 */
@RestController
@Profile("demo")
@RequestMapping(path = "/api/internal/conversations", produces = MediaType.APPLICATION_JSON_VALUE)
class StaffConversationController {

    private final CurrentIdentity identity;
    private final StaffChat chat;

    StaffConversationController(CurrentIdentity identity, StaffChat chat) {
        this.identity = identity;
        this.chat = chat;
    }

    @GetMapping("/{conversationId}/messages")
    List<Chat.ChatMessage> messages(@PathVariable String conversationId) {
        return chat.messages(session(), conversation(conversationId));
    }

    @PostMapping(path = "/{conversationId}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void post(@PathVariable String conversationId, @Valid @RequestBody StaffMessageRequest request) {
        chat.post(session(), conversation(conversationId), request.text());
    }

    /** EP-08b: the newest quote of a held conversation, with prices; no confirmation token. */
    @GetMapping("/{conversationId}/quote")
    StaffQuoteView quote(@PathVariable String conversationId) {
        StaffQuotes.StaffQuoteView quote = chat.quote(session(), conversation(conversationId));
        return new StaffQuoteView(quote.quoteRef(), quote.version(), quote.status(),
                quote.lines().stream().map(line -> new LineView(line.skuCode(), line.quantity(),
                        amount(line.unitPrice()), amount(line.lineTotal()))).toList(),
                amount(quote.total()), quote.total().currency().getCurrencyCode(), quote.validUntil().toString(),
                quote.notes());
    }

    private static String amount(Money money) {
        return money.amount().toPlainString();
    }

    private SessionIdentity session() {
        return identity.current().orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    /** A malformed ID is the same 404 as a conversation the caller does not hold. */
    private static ConversationId conversation(String value) {
        try {
            return new ConversationId(value);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(ErrorCode.NOT_FOUND);
        }
    }

    /** Amounts as exact strings, like the customer's card; never a confirmation token. */
    record StaffQuoteView(String quoteRef, int version, String status, List<LineView> lines, String total,
            String currency, String validUntil, List<String> notes) {
    }

    record LineView(String sku, int quantity, String unitPrice, String lineTotal) {
    }

    record StaffMessageRequest(@NotBlank @Size(max = 2000) String text) {

        @Override
        public String toString() {
            return "StaffMessageRequest[redacted]";
        }
    }
}
