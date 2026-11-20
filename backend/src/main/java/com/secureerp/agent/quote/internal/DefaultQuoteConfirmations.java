package com.secureerp.agent.quote.internal;

import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.identity.AuthenticatedCustomerRef;
import com.secureerp.agent.identity.CustomerLinks;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.identity.Team;
import com.secureerp.agent.identity.spi.ErpCustomerLink;
import com.secureerp.agent.order.DraftOrderState;
import com.secureerp.agent.order.DraftOrderSubmission;
import com.secureerp.agent.order.DraftOrders;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.platform.Money;
import com.secureerp.agent.quote.QuoteCard;
import com.secureerp.agent.quote.QuoteConfirmations;
import com.secureerp.agent.quote.QuoteStaffRequired;
import com.secureerp.agent.quote.spi.AvailabilityPort;
import com.secureerp.agent.quote.spi.CreditEligibilityPort;
import com.secureerp.agent.quote.spi.EligibilityStatus;
import com.secureerp.agent.quote.spi.PriceQuote;
import com.secureerp.agent.quote.spi.PricingPort;
import com.secureerp.agent.quote.spi.QuoteSourceUnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.stereotype.Service;

/**
 * Confirmation of a quote version (UC-04, DATA_FLOW §2; ADR-007, ADR-009 ACCEPTED 2026-11-19).
 *
 * <ol>
 *   <li>Authorization from the session only: a linked customer, and the quote row is read by its
 *       reference, version <em>and</em> the customer digest. An unknown reference, another customer's
 *       quote, an unknown version and a wrong token are the same 404 (TH-06, TH-26). Attempts are
 *       limited per customer and recorded.</li>
 *   <li>State guards: only an ISSUED, unexpired version goes on (409 stale, 410 expired). A version
 *       already confirmed or refused returns its current state and never creates a second order.</li>
 *   <li>Re-check of credit, price and stock through the quote ports, outside any transaction (BR-15).
 *       Not eligible, no price or not enough stock → CONFIRMATION_BLOCKED and staff (BR-38); a changed
 *       price → the old version SUPERSEDED, a new one ISSUED, 409 QUOTE_REPRICED (BR-16); the ERP not
 *       answering → 503 and staff, the quote unchanged.</li>
 *   <li>Confirmation and the draft order request commit in one transaction; the ERP is then asked
 *       once by {@code order}, and the quote mirrors the request's state.</li>
 * </ol>
 * Logs carry outcome codes only: never a reference, a token, a reason, a price or a stock number.
 */
@Service
class DefaultQuoteConfirmations implements QuoteConfirmations {

    private static final Logger log = LoggerFactory.getLogger(DefaultQuoteConfirmations.class);
    private static final Duration ATTEMPT_WINDOW = Duration.ofHours(1);

    private final PricingPort pricing;
    private final AvailabilityPort availability;
    private final CreditEligibilityPort credit;
    private final CustomerLinks links;
    private final ConfirmationStore store;
    private final DraftOrders orders;
    private final QuoteProperties properties;
    private final ConfirmationProperties limits;
    private final ApplicationEventPublisher events;
    private final QuoteTokens tokens = new QuoteTokens();
    private final Clock clock;

    DefaultQuoteConfirmations(PricingPort pricing, AvailabilityPort availability, CreditEligibilityPort credit,
            CustomerLinks links, ConfirmationStore store, DraftOrders orders, QuoteProperties properties,
            ConfirmationProperties limits, ApplicationEventPublisher events, Clock clock) {
        this.pricing = pricing;
        this.availability = availability;
        this.credit = credit;
        this.links = links;
        this.store = store;
        this.orders = orders;
        this.properties = properties;
        this.limits = limits;
        this.events = events;
        this.clock = clock;
    }

    /** What the re-check found. */
    private sealed interface Recheck {

        record Passed() implements Recheck {
        }

        record Blocked(NotQuotedReason reason) implements Recheck {
        }

        record Repriced(List<QuoteCalculator.PricedLine> lines) implements Recheck {
        }

        record Unavailable() implements Recheck {
        }
    }

    @Override
    public Confirmation confirm(SessionIdentity identity, String quoteRef, int version, String confirmationToken) {
        AuthenticatedCustomerRef customer = customer(identity);
        String digest = customer.bindingDigest();
        Instant now = clock.instant();
        // Counted and reserved before anything else, so parallel requests cannot pass the limit (TH-26).
        long attempt;
        try {
            attempt = store.admitAttempt(digest, now.minus(ATTEMPT_WINDOW), limits.maxAttemptsPerHour(), now)
                    .orElseThrow(() -> {
                        log.warn("quote.confirm outcome=RATE_LIMITED");
                        return new ApplicationException(ErrorCode.TOO_MANY_REQUESTS);
                    });
        } catch (PessimisticLockingFailureException busy) {
            log.warn("quote.confirm outcome=CONFLICT");
            throw new ApplicationException(ErrorCode.CONFLICT);
        } catch (CannotCreateTransactionException noConnection) {
            // Too many confirmations of one customer wait for its lock: a safe "try again", not a 500.
            log.warn("quote.confirm outcome=BUSY");
            throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        try {
            return confirm(new Attempt(attempt, digest, customer, identity.team().orElse(null)), quoteRef, version,
                    confirmationToken, now);
        } catch (PessimisticLockingFailureException busy) {
            // Another write of this customer or conversation held the lock too long: nothing changed here.
            finish(attempt, null, ConfirmationStore.Attempt.CONFLICT, null);
            log.warn("quote.confirm outcome=CONFLICT");
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }

    /** One admitted attempt of the session customer; {@code team} is the team serving them (EP-08a, may be null). */
    private record Attempt(long id, String digest, AuthenticatedCustomerRef customer, Team team) {
    }

    private Confirmation confirm(Attempt attempt, String quoteRef, int version, String confirmationToken,
            Instant now) {
        Optional<ConfirmationStore.QuoteRow> found = wellFormed(quoteRef, version, confirmationToken)
                ? store.find(quoteRef, version, attempt.digest()).filter(row -> tokenMatches(row, confirmationToken))
                : Optional.empty();
        if (found.isEmpty()) {
            return refuse(attempt, null, ConfirmationStore.Attempt.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        ConfirmationStore.QuoteRow row = found.get();
        if (row.status() != QuoteStatus.ISSUED) {
            return settled(attempt, row);
        }
        if (!row.validUntil().isAfter(now)) {
            store.expire(row);
            return refuse(attempt, row, ConfirmationStore.Attempt.EXPIRED, ErrorCode.QUOTE_EXPIRED);
        }
        Optional<ErpCustomerLink> erpCustomer = links.erpLinkOf(attempt.customer());
        if (erpCustomer.isEmpty()) {
            return refuse(attempt, row, ConfirmationStore.Attempt.NOT_FOUND, ErrorCode.CUSTOMER_NOT_LINKED);
        }
        List<ConfirmationStore.LineRow> lines = store.lines(row.id());

        return switch (recheck(erpCustomer.get(), row, lines)) {
            case Recheck.Unavailable unavailable -> {
                finish(attempt.id(), row.id(), ConfirmationStore.Attempt.SOURCE_UNAVAILABLE, null);
                events.publishEvent(new QuoteStaffRequired(new ConversationId(row.conversationId()),
                        attempt.digest(), QuoteStaffRequired.Reason.SOURCE_UNAVAILABLE));
                log.warn("quote.confirm outcome=SOURCE_UNAVAILABLE");
                throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
            }
            case Recheck.Blocked blocked -> {
                if (!store.block(row, blocked.reason(), lines.size())) {
                    yield changedMeanwhile(attempt, row);
                }
                finish(attempt.id(), row.id(), ConfirmationStore.Attempt.BLOCKED, blocked.reason());
                log.info("quote.confirm outcome=CONFIRMATION_BLOCKED");
                yield new Confirmation(row.publicRef(), row.version(), State.CONFIRMATION_BLOCKED);
            }
            case Recheck.Repriced repriced -> {
                QuoteCalculator.Calculation calculation = QuoteCalculator.calculate(repriced.lines(),
                        Money.zero(row.currency()), row.moneyScale(), row.rounding());
                if (!store.reissue(row, calculation, properties.validity())) {
                    yield changedMeanwhile(attempt, row);
                }
                yield refuse(attempt, row, ConfirmationStore.Attempt.REPRICED, ErrorCode.QUOTE_REPRICED);
            }
            case Recheck.Passed passed -> {
                DraftOrderSubmission submission = submission(row, erpCustomer.get(), lines);
                if (!store.confirm(row, () -> orders.register(submission, attempt.team()))) {
                    yield changedMeanwhile(attempt, row);
                }
                finish(attempt.id(), row.id(), ConfirmationStore.Attempt.ACCEPTED, null);
                log.info("quote.confirm outcome=ACCEPTED lines={}", lines.size());
                yield submit(row, submission);
            }
        };
    }

    @Override
    public Overview current(SessionIdentity identity, String quoteRef) {
        AuthenticatedCustomerRef customer = customer(identity);
        String digest = customer.bindingDigest();
        Instant now = clock.instant();
        ConfirmationStore.QuoteRow row = (quoteRef != null && QuoteTokens.PUBLIC_REF.matcher(quoteRef).matches()
                ? store.latest(quoteRef, digest) : Optional.<ConfirmationStore.QuoteRow>empty())
                .orElseThrow(() -> {
                    log.warn("quote.read denied code=FORBIDDEN_RESOURCE");
                    return new ApplicationException(ErrorCode.NOT_FOUND);
                });
        if (row.status() == QuoteStatus.ORDER_SUBMITTING || row.status() == QuoteStatus.ORDER_OUTCOME_UNKNOWN) {
            // Read repair: the draft order request is the source of truth (ADR-007).
            return new Overview(row.publicRef(), row.version(), repaired(row).name(), null);
        }
        if (row.status() != QuoteStatus.ISSUED) {
            return new Overview(row.publicRef(), row.version(), row.status().name(), null);
        }
        if (!row.validUntil().isAfter(now)) {
            return new Overview(row.publicRef(), row.version(), QuoteStatus.EXPIRED.name(), null);
        }
        QuoteCard card;
        try {
            List<QuoteCard.Line> lines = store.lines(row.id()).stream()
                    .map(line -> DefaultQuoteCards.line(line.sku(), line.quantity(), line.unitPrice(),
                            line.lineTotal(), row.moneyScale(), row.currency()))
                    .toList();
            // DESIGN PROPOSAL (A-41): every render issues a new token, so only the latest card confirms.
            String token = tokens.newConfirmationToken();
            card = new QuoteCard(row.publicRef(), row.version(), lines,
                    DefaultQuoteCards.exact(row.total(), row.moneyScale(), row.currency()), row.validUntil(),
                    List.of(QuoteCard.NOTE_VAT, QuoteCard.NOTE_FITMENT), token);
            // Stored only once the card is complete, so a card that cannot be built leaves the old token valid.
            if (links.erpLinkOf(customer).isEmpty() || !store.armConfirmation(row.id(), QuoteTokens.hash(token))) {
                throw new ApplicationException(ErrorCode.NOT_FOUND);
            }
        } catch (ArithmeticException | IllegalArgumentException invalid) {
            // A stored snapshot that no longer fits the money rules renders nothing (as QuoteCards.render).
            log.warn("quote.read outcome=INVALID exception={}", invalid.getClass().getSimpleName());
            throw new ApplicationException(ErrorCode.NOT_FOUND);
        }
        log.info("quote.read outcome=RENDERED");
        return new Overview(row.publicRef(), row.version(), row.status().name(), card);
    }

    /**
     * Read repair: the draft order request is the source of truth (ADR-007). The answer is the status
     * the quote really has afterwards; a repair that fails or is refused changes nothing and is logged.
     */
    private QuoteStatus repaired(ConfirmationStore.QuoteRow row) {
        try {
            QuoteStatus target = orders.stateOf(row.publicRef(), row.version()).map(QuoteStatus::mirrorOf)
                    .orElse(row.status());
            return switch (store.mirror(row.publicRef(), row.version(), target)) {
                case CHANGED -> target;
                case UNCHANGED, NO_QUOTE -> row.status();
                case REFUSED -> {
                    log.error("quote.read mirror_refused order_state={}", target);
                    yield row.status();
                }
            };
        } catch (RuntimeException failure) {
            log.error("quote.read repair_failed exception={}", failure.getClass().getSimpleName());
            return row.status();
        }
    }

    /** RBAC_MATRIX: only the linked customer confirms; identity comes from the session (BR-01). */
    private static AuthenticatedCustomerRef customer(SessionIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        if (identity.role() != Role.CUST) {
            throw new ApplicationException(ErrorCode.FORBIDDEN);
        }
        return identity.customer().orElseThrow(() -> new ApplicationException(ErrorCode.CUSTOMER_NOT_LINKED));
    }

    private static boolean wellFormed(String quoteRef, int version, String token) {
        return quoteRef != null && QuoteTokens.PUBLIC_REF.matcher(quoteRef).matches() && version >= 1
                && token != null && QuoteTokens.CONFIRMATION.matcher(token).matches();
    }

    private static boolean tokenMatches(ConfirmationStore.QuoteRow row, String token) {
        return row.confirmTokenHash() != null && MessageDigest.isEqual(
                row.confirmTokenHash().getBytes(StandardCharsets.US_ASCII),
                QuoteTokens.hash(token).getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Records how the attempt ended. Best effort: the attempt is already counted (STARTED), and an
     * order that was accepted must still be sent when this bookkeeping write fails.
     */
    private void finish(long attemptId, Long quoteId, ConfirmationStore.Attempt outcome, NotQuotedReason reason) {
        try {
            store.finishAttempt(attemptId, quoteId, outcome, reason);
        } catch (RuntimeException auditFailure) {
            log.warn("quote.confirm audit_failed exception={}", auditFailure.getClass().getSimpleName());
        }
    }

    /** Records the attempt, then answers with the error code (the return type only satisfies callers). */
    private Confirmation refuse(Attempt attempt, ConfirmationStore.QuoteRow row, ConfirmationStore.Attempt outcome,
            ErrorCode code) {
        finish(attempt.id(), row == null ? null : row.id(), outcome, null);
        log.info("quote.confirm outcome={}", outcome);
        throw new ApplicationException(code);
    }

    /** The quote left ISSUED between the read and the write (optimistic check): answer from what it is now. */
    private Confirmation changedMeanwhile(Attempt attempt, ConfirmationStore.QuoteRow stale) {
        ConfirmationStore.QuoteRow fresh = store.find(stale.publicRef(), stale.version(), attempt.digest())
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND));
        if (fresh.status() == QuoteStatus.ISSUED) {
            // Still ISSUED: it expired while the ERP was asked. The next attempt answers 410.
            return refuse(attempt, fresh, ConfirmationStore.Attempt.CONFLICT, ErrorCode.CONFLICT);
        }
        return settled(attempt, fresh);
    }

    /** A version that is no longer ISSUED: an error for a retired one, the current state for the rest. */
    private Confirmation settled(Attempt attempt, ConfirmationStore.QuoteRow row) {
        return switch (row.status()) {
            case SUPERSEDED, CANCELLED ->
                    refuse(attempt, row, ConfirmationStore.Attempt.STALE_VERSION, ErrorCode.QUOTE_STALE_VERSION);
            case EXPIRED -> refuse(attempt, row, ConfirmationStore.Attempt.EXPIRED, ErrorCode.QUOTE_EXPIRED);
            case DRAFT, ISSUED -> refuse(attempt, row, ConfirmationStore.Attempt.NOT_FOUND, ErrorCode.NOT_FOUND);
            case CONFIRMATION_BLOCKED -> replay(attempt, row, State.CONFIRMATION_BLOCKED);
            case ORDER_FAILED, REJECTED -> replay(attempt, row, State.ORDER_FAILED);
            case DRAFT_ORDER_CREATED, STAFF_APPROVED -> replay(attempt, row, State.DRAFT_ORDER_CREATED);
            case CONFIRMED, ORDER_SUBMITTING, ORDER_OUTCOME_UNKNOWN -> {
                finish(attempt.id(), row.id(), ConfirmationStore.Attempt.REPLAY, null);
                Optional<ErpCustomerLink> erpCustomer = links.erpLinkOf(attempt.customer());
                if (erpCustomer.isEmpty()) {
                    throw new ApplicationException(ErrorCode.CUSTOMER_NOT_LINKED);
                }
                // Order sends only what was registered at confirmation (digest), and only within its
                // submit timeout; after that the request is an unknown outcome for staff.
                yield submit(row, submission(row, erpCustomer.get(), store.lines(row.id())));
            }
        };
    }

    private Confirmation replay(Attempt attempt, ConfirmationStore.QuoteRow row, State state) {
        finish(attempt.id(), row.id(), ConfirmationStore.Attempt.REPLAY, null);
        log.info("quote.confirm outcome=REPLAY state={}", state);
        return new Confirmation(row.publicRef(), row.version(), state);
    }

    /** The stored snapshot as a draft order; unit prices at the scale the card shows, not NUMERIC padding. */
    private static DraftOrderSubmission submission(ConfirmationStore.QuoteRow row, ErpCustomerLink erpCustomer,
            List<ConfirmationStore.LineRow> lines) {
        return new DraftOrderSubmission(row.publicRef(), row.version(), erpCustomer,
                lines.stream().map(line -> new DraftOrderSubmission.Line(line.sku(), line.quantity(),
                        DefaultQuoteCards.unitPrice(line.unitPrice(), row.moneyScale(), row.currency()))).toList());
    }

    /**
     * Hands the confirmed version to order and mirrors the request's state. Order sends it at most
     * once, so this is safe to call again for a version already submitting or unknown. Whatever fails
     * after the confirmation is committed, the customer gets the state as far as it is known; the
     * request itself is the source of truth and the quote catches up on the next read.
     */
    private Confirmation submit(ConfirmationStore.QuoteRow row, DraftOrderSubmission submission) {
        DraftOrderState state;
        try {
            state = orders.dispatch(submission);
        } catch (RuntimeException failure) {
            // The request stays registered; order turns it into an unknown outcome after its timeout.
            log.error("quote.confirm dispatch_failed exception={}", failure.getClass().getSimpleName());
            return new Confirmation(row.publicRef(), row.version(), State.ORDER_SUBMITTING);
        }
        return answer(row, state);
    }

    private Confirmation answer(ConfirmationStore.QuoteRow row, DraftOrderState state) {
        QuoteStatus mirrored = QuoteStatus.mirrorOf(state);
        try {
            if (store.mirror(row.publicRef(), row.version(), mirrored) == ConfirmationStore.Mirrored.REFUSED) {
                // The quote moved on (e.g. staff reconciled) after this state was read.
                log.warn("quote.confirm mirror_refused order_state={}", mirrored);
            }
        } catch (RuntimeException failure) {
            log.error("quote.confirm mirror_failed exception={}", failure.getClass().getSimpleName());
        }
        log.info("quote.confirm order_state={}", mirrored);
        return new Confirmation(row.publicRef(), row.version(), switch (mirrored) {
            case DRAFT_ORDER_CREATED -> State.DRAFT_ORDER_CREATED;
            case ORDER_OUTCOME_UNKNOWN -> State.ORDER_OUTCOME_UNKNOWN;
            case ORDER_FAILED -> State.ORDER_FAILED;
            default -> State.ORDER_SUBMITTING;
        });
    }

    private Recheck recheck(ErpCustomerLink customer, ConfirmationStore.QuoteRow row,
            List<ConfirmationStore.LineRow> lines) {
        try {
            EligibilityStatus status = credit.check(customer);
            if (status != EligibilityStatus.ELIGIBLE) {
                return new Recheck.Blocked(status == EligibilityStatus.NOT_ELIGIBLE
                        ? NotQuotedReason.CREDIT_NOT_ELIGIBLE : NotQuotedReason.CREDIT_REVIEW_REQUIRED);
            }
            boolean repriced = false;
            List<QuoteCalculator.PricedLine> priced = new ArrayList<>();
            for (ConfirmationStore.LineRow line : lines) {
                Sku sku = new Sku(line.sku());
                Optional<PriceQuote> price = pricing.priceFor(customer, sku);
                if (price.isEmpty()) {
                    return new Recheck.Blocked(NotQuotedReason.PRICE_NOT_FOUND);
                }
                if (!price.get().unitPrice().currency().equals(row.currency())) {
                    return new Recheck.Blocked(NotQuotedReason.PRICE_CURRENCY_MISMATCH);
                }
                OptionalInt stock = availability.quantityAvailable(sku);
                if (stock.isEmpty() || stock.getAsInt() < line.quantity()) {
                    return new Recheck.Blocked(NotQuotedReason.INSUFFICIENT_STOCK);
                }
                repriced |= differs(line, price.get());
                priced.add(new QuoteCalculator.PricedLine(sku, line.quantity(), price.get()));
            }
            return repriced ? new Recheck.Repriced(priced) : new Recheck.Passed();
        } catch (QuoteSourceUnavailableException sourceDown) {
            return new Recheck.Unavailable();
        } catch (RuntimeException unexpected) {
            log.warn("quote.confirm recheck_failed exception={}", unexpected.getClass().getSimpleName());
            return new Recheck.Unavailable();
        }
    }

    /**
     * The snapshot differs when the amount (compared by value, ADR-014), the price source or a
     * revision both sides have differs. A revision only one side has says nothing (D-01 OPEN).
     */
    static boolean differs(ConfirmationStore.LineRow snapshot, PriceQuote current) {
        return snapshot.unitPrice().compareTo(current.unitPrice().amount()) != 0
                || !snapshot.priceSource().equals(current.priceSource())
                || (snapshot.priceRevision() != null && current.revision() != null
                        && !snapshot.priceRevision().equals(current.revision()));
    }
}
