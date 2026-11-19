package com.secureerp.agent.quote.adapter.web;

import com.secureerp.agent.identity.CurrentIdentity;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.platform.Money;
import com.secureerp.agent.quote.QuoteCard;
import com.secureerp.agent.quote.QuoteConfirmations;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customer's own quote and its confirmation (UC-04, DATA_FLOW §2 step 1; SYSTEM_ARCHITECTURE §8).
 * Registered only with profile {@code demo} (ADR-015). Transport only: the customer comes from the
 * session ({@link CurrentIdentity}), never from the body, a header or the URL (ARCH-08b); the quote
 * reference in the URL is resolved against the session customer by the service, and a quote that is
 * not theirs is the same 404 as a missing one. The request body is capped by the demo request size
 * filter and unknown JSON fields are rejected.
 *
 * <p>Confirmation answers: 200 when the request was settled (draft order created, blocked or
 * failed: see {@code status}), 202 while the ERP outcome is not known yet or is unknown.
 */
@RestController
@Profile("demo")
@RequestMapping(path = "/api/demo/quotes", produces = MediaType.APPLICATION_JSON_VALUE)
class QuoteController {

    private final CurrentIdentity identity;
    private final QuoteConfirmations confirmations;

    QuoteController(CurrentIdentity identity, QuoteConfirmations confirmations) {
        this.identity = identity;
        this.confirmations = confirmations;
    }

    @GetMapping("/{quoteRef}")
    QuoteView get(@PathVariable String quoteRef) {
        QuoteConfirmations.Overview overview = confirmations.current(session(), quoteRef);
        return new QuoteView(overview.quoteRef(), overview.version(), overview.status(),
                overview.card().map(QuoteCardView::of).orElse(null));
    }

    @PostMapping(path = "/{quoteRef}/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ConfirmationView> confirm(@PathVariable String quoteRef, @Valid @RequestBody ConfirmRequest request) {
        QuoteConfirmations.Confirmation result =
                confirmations.confirm(session(), quoteRef, request.version(), request.confirmationToken());
        QuoteConfirmations.State state = result.state();
        boolean pending = state == QuoteConfirmations.State.ORDER_SUBMITTING
                || state == QuoteConfirmations.State.ORDER_OUTCOME_UNKNOWN;
        return ResponseEntity.status(pending ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(new ConfirmationView(result.quoteRef(), result.version(), state.name(), state.handedOff()));
    }

    private SessionIdentity session() {
        return identity.current().orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    record ConfirmRequest(@NotNull @Min(1) @Max(999_999) Integer version,
            @NotBlank @Size(max = 64) String confirmationToken) {

        @Override
        public String toString() {
            return "ConfirmRequest[redacted]";
        }
    }

    /** {@code status} is the quote state; the reason of a blocked or failed confirmation is never included. */
    record ConfirmationView(String quoteRef, int version, String status, boolean handedOff) {
    }

    record QuoteView(String quoteRef, int version, String status, QuoteCardView quoteCard) {
    }

    /** Server-rendered quote for the session customer only (ARCH-13a/13b; amounts as exact strings). */
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
