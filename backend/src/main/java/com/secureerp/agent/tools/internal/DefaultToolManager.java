package com.secureerp.agent.tools.internal;

import com.secureerp.agent.catalog.CatalogMatch;
import com.secureerp.agent.catalog.CatalogSearch;
import com.secureerp.agent.catalog.spi.CatalogUnavailableException;
import com.secureerp.agent.catalog.spi.ExactProductQuery;
import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.gateway.api.AvailabilityBucket;
import com.secureerp.agent.gateway.api.HandoffResult;
import com.secureerp.agent.gateway.api.ProductRef;
import com.secureerp.agent.gateway.api.ProductSearchResult;
import com.secureerp.agent.gateway.api.Provenance;
import com.secureerp.agent.gateway.api.QuoteRequestResult;
import com.secureerp.agent.gateway.api.ToolErrorResult;
import com.secureerp.agent.gateway.api.ToolProposal;
import com.secureerp.agent.gateway.api.ToolResultDto;
import com.secureerp.agent.gateway.api.ToolSpec;
import com.secureerp.agent.identity.AuthenticatedCustomerRef;
import com.secureerp.agent.identity.Channel;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.quote.QuoteOutcome;
import com.secureerp.agent.quote.QuoteRequest;
import com.secureerp.agent.quote.QuoteService;
import com.secureerp.agent.tools.ToolCallContext;
import com.secureerp.agent.tools.ToolManager;
import com.secureerp.agent.tools.ToolOutcome;
import com.secureerp.agent.tools.internal.ToolArguments.HandoffRequest;
import com.secureerp.agent.tools.internal.ToolArguments.QuoteLines;
import com.secureerp.agent.tools.internal.ToolArguments.SearchByCategoryAndModel;
import com.secureerp.agent.tools.internal.ToolArguments.SearchByOe;
import com.secureerp.agent.tools.internal.ToolArguments.SearchByVinRef;
import com.secureerp.agent.tools.spi.ConversationAccessPort;
import com.secureerp.agent.tools.spi.ConversationAccessPort.Access;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Tool pipeline (TOOL_CONTRACTS §1, ADR-006 ACCEPTED 2026-11-18), for every call:
 * <ol>
 *   <li>admit: quotas per turn, conversation and principal, and the attempt is recorded (every
 *       attempt counts, also unknown tools and invalid arguments);</li>
 *   <li>name: exact match in the registry and allowed for the role and channel;</li>
 *   <li>conversation: owned by the principal and not locked;</li>
 *   <li>arguments: size, depth and strict schema ({@link ArgumentParser});</li>
 *   <li>resources: every productRef resolves for this conversation and is VERIFIED;</li>
 *   <li>execute, then map to a typed {@code gateway.api} result (A-20).</li>
 * </ol>
 * Every failure is a {@link ToolErrorResult} code; logs carry tool name, code and decision only.
 *
 * <p>{@code vinRef} search uses the conversation's server-side VIN (EP-05); {@code requestQuote} calls
 * the quote service (EP-06). {@code requestHandoff} (EP-08b) answers {@code {"accepted":true}} with
 * {@code handoffRequired} and the requested reason; conversation opens the handoff.
 */
@Service
class DefaultToolManager implements ToolManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultToolManager.class);

    /** Catalog SKU and OE as the model may see them (TOOL_CONTRACTS §3.2). */
    private static final Pattern CATALOG_CODE = Pattern.compile("^[A-Z0-9-]{3,30}$");
    private static final Pattern DIGIT_RUN = Pattern.compile("\\d{7,}");
    /** A syntactically valid token used only to validate a candidate before a real one is issued. */
    private static final String PLACEHOLDER_REF = ProductRef.PRODUCT_REF_PREFIX + "AAAAAAAAAAAAAAAAAAAAAA";
    private static final ConversationAccessPort DENY_ALL = (principal, conversation) -> Access.NOT_OWNED;

    private final ToolInvocationStore invocations;
    private final ProductRefRegistry productRefs;
    private final CatalogSearch catalog;
    private final QuoteService quotes;
    private final ObjectProvider<ConversationAccessPort> conversations;
    private final ToolLimits limits;
    private final Clock clock;

    DefaultToolManager(ToolInvocationStore invocations, ProductRefRegistry productRefs, CatalogSearch catalog,
            QuoteService quotes, ObjectProvider<ConversationAccessPort> conversations, ToolLimits limits, Clock clock) {
        this.invocations = invocations;
        this.productRefs = productRefs;
        this.catalog = catalog;
        this.quotes = quotes;
        this.conversations = conversations;
        this.limits = limits;
        this.clock = clock;
    }

    @Override
    public List<ToolSpec> toolsFor(Role role, Channel channel) {
        return Arrays.stream(ToolName.values()).filter(tool -> tool.allows(role, channel)).map(ToolName::spec).toList();
    }

    @Override
    public ToolOutcome execute(ToolCallContext context, ToolProposal proposal) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(proposal, "proposal");
        Instant now = clock.instant();
        Optional<ToolName> tool = ToolName.fromWire(proposal.name());
        String toolName = tool.map(ToolName::wireName).orElse(ToolInvocationStore.UNKNOWN_TOOL);
        Access access;
        OptionalLong admitted;
        ConversationAccessPort port = DENY_ALL;
        try {
            // Ownership first: a call on someone else's conversation counts against the caller only
            // (EP-03 security review M-2). Exactly one port, else deny all (a @Primary bean does not
            // make an ambiguous setup acceptable; second review LOW-2).
            List<ConversationAccessPort> ports = conversations.orderedStream().limit(2).toList();
            port = ports.size() == 1 ? ports.getFirst() : DENY_ALL;
            Access checked = port.check(context.principal(), context.conversation());
            // Anything but ACTIVE or LOCKED, including null, is "not owned" (second review LOW-1).
            access = checked == Access.ACTIVE || checked == Access.LOCKED ? checked : Access.NOT_OWNED;
            admitted = invocations.admit(context, toolName, access != Access.NOT_OWNED, now, limits);
        } catch (RuntimeException storeOrPort) {
            // Every outcome is typed (EP-03 reviews L4): quota/audit store or port failure fails closed.
            log.warn("tools.call admit_failed exception={} tool={} turn={}", storeOrPort.getClass().getSimpleName(),
                    toolName, context.turnId());
            return new ToolOutcome(new ToolErrorResult(ToolErrorResult.Code.TOOL_UNAVAILABLE), true);
        }
        if (admitted.isEmpty()) {
            log.warn("tools.call decision=REJECTED tool={} code=QUOTA_EXCEEDED turn={}", toolName, context.turnId());
            return new ToolOutcome(new ToolErrorResult(ToolErrorResult.Code.QUOTA_EXCEEDED), true);
        }
        try {
            Executed executed = run(context, tool, access, port, proposal, now);
            ToolResultDto result = executed.result();
            invocations.complete(admitted.getAsLong(), ToolInvocationStore.Decision.INVOKED, null);
            log.info("tools.call decision=INVOKED tool={} turn={}", toolName, context.turnId());
            // TOOL_CONTRACTS §3.3: NOT_QUOTED always comes with a staff handoff; §3.4: an accepted
            // handoff request locks the bot (EP-08b).
            boolean handoff = executed.requested() != null || result instanceof QuoteRequestResult quote
                    && quote.status() == QuoteRequestResult.Status.NOT_QUOTED;
            return new ToolOutcome(result, handoff, executed.requested());
        } catch (ToolRejection rejection) {
            return reject(context, admitted.getAsLong(), toolName, rejection, now);
        } catch (RuntimeException unexpected) {
            log.warn("tools.call unexpected exception={} tool={} turn={}", unexpected.getClass().getSimpleName(),
                    toolName, context.turnId());
            return reject(context, admitted.getAsLong(), toolName,
                    ToolRejection.of(ToolErrorResult.Code.TOOL_UNAVAILABLE), now);
        }
    }

    private ToolOutcome reject(ToolCallContext context, long invocationId, String toolName, ToolRejection rejection,
            Instant now) {
        ToolErrorResult.Code code = rejection.code();
        boolean handoff;
        try {
            invocations.complete(invocationId, ToolInvocationStore.Decision.REJECTED, code.name());
            handoff = rejection.handoff() || code == ToolErrorResult.Code.FORBIDDEN_RESOURCE
                    || invocations.rejectionsSince(context, now.minus(ToolLimits.WINDOW))
                            >= limits.rejectionsBeforeHandoff();
        } catch (RuntimeException store) {
            log.warn("tools.call audit_failed exception={} tool={} turn={}", store.getClass().getSimpleName(), toolName,
                    context.turnId());
            handoff = true;
        }
        log.warn("tools.call decision=REJECTED tool={} code={} handoff={} turn={}", toolName, code, handoff,
                context.turnId());
        return new ToolOutcome(new ToolErrorResult(code), handoff);
    }

    /** What a call produced; {@code requested} is set only for an accepted handoff request. */
    private record Executed(ToolResultDto result, ToolOutcome.RequestedHandoff requested) {
    }

    private Executed run(ToolCallContext context, Optional<ToolName> tool, Access access,
            ConversationAccessPort port, ToolProposal proposal, Instant now) {
        ToolName name = tool.orElseThrow(() -> ToolRejection.of(ToolErrorResult.Code.TOOL_UNKNOWN));
        if (!name.allows(context.role(), context.channel())) {
            throw ToolRejection.of(ToolErrorResult.Code.TOOL_UNKNOWN);
        }
        if (access != Access.ACTIVE) {
            throw ToolRejection.of(ToolErrorResult.Code.FORBIDDEN_RESOURCE);
        }
        ToolArguments arguments = ArgumentParser.parse(name, proposal.argumentsJson(), limits);
        if (arguments instanceof HandoffRequest handoff) {
            // EP-08b (TOOL_CONTRACTS §3.4): the model only proposes; conversation opens the handoff for the
            // session's conversation, and the reason stays internal (the model sees {"accepted":true}).
            return new Executed(new HandoffResult(true), requested(handoff.reason()));
        }
        return new Executed(perform(context, port, arguments, now), null);
    }

    private static ToolOutcome.RequestedHandoff requested(HandoffRequest.Reason reason) {
        return switch (reason) {
            case CUSTOMER_REQUEST -> ToolOutcome.RequestedHandoff.CUSTOMER_REQUEST;
            case UNSURE -> ToolOutcome.RequestedHandoff.UNSURE;
            case OUT_OF_SCOPE -> ToolOutcome.RequestedHandoff.OUT_OF_SCOPE;
        };
    }

    private ToolResultDto perform(ToolCallContext context, ConversationAccessPort port, ToolArguments arguments,
            Instant now) {
        return switch (arguments) {
            case SearchByOe byOe -> {
                ExactProductQuery query = query(() -> new ExactProductQuery.ByOe(byOe.oe()));
                yield search(context, () -> catalog.search(query), now);
            }
            case SearchByCategoryAndModel byModel -> {
                ExactProductQuery query = query(() ->
                        new ExactProductQuery.ByCategoryAndModel(byModel.category(), byModel.vehicleModel()));
                yield search(context, () -> catalog.search(query), now);
            }
            // The VIN never passes through here: catalog resolves the conversation's current VIN
            // (TOOL_CONTRACTS §3.1). No current VIN: TOOL_UNAVAILABLE (owner decision 2026-11-18).
            case SearchByVinRef byVin -> search(context, () -> catalog.searchByConversationVin(context.conversation())
                    .orElseThrow(() -> ToolRejection.of(ToolErrorResult.Code.TOOL_UNAVAILABLE)), now);
            case QuoteLines quote -> requestQuote(context, port, quote, now);
            case HandoffRequest handoff -> throw new IllegalStateException("handled before");
        };
    }

    /** A catalog query type that still refuses a parsed value is a schema error (arch review 2, LOW-3). */
    private static ExactProductQuery query(Supplier<ExactProductQuery> factory) {
        try {
            return factory.get();
        } catch (IllegalArgumentException e) {
            throw ToolRejection.of(ToolErrorResult.Code.TOOL_SCHEMA);
        }
    }

    /** T-TOOL-04: every productRef came from a search in this conversation, is unexpired and VERIFIED. */
    private List<QuoteRequest.Line> requireOwnedVerifiedProducts(ToolCallContext context, QuoteLines quote,
            Instant now) {
        List<QuoteRequest.Line> lines = new ArrayList<>();
        for (QuoteLines.Line line : quote.lines()) {
            Optional<ProductRefRegistry.IssuedProductRef> issued =
                    productRefs.resolve(context.conversation(), line.productRef(), now);
            if (issued.isEmpty()
                    || issued.get().provenance() != com.secureerp.agent.catalog.spi.Provenance.VERIFIED) {
                throw ToolRejection.of(ToolErrorResult.Code.FORBIDDEN_RESOURCE);
            }
            lines.add(new QuoteRequest.Line(new Sku(issued.get().sku()), line.quantity()));
        }
        return lines;
    }

    /**
     * EP-06: quote only the SKUs behind this conversation's VERIFIED productRefs, for the session's
     * customer (never from arguments). The model sees ISSUED + handle or one NOT_QUOTED (BR-35); NOT_QUOTED
     * always hands off ({@link #execute}). The same SKU twice is a schema error the model can fix.
     */
    private ToolResultDto requestQuote(ToolCallContext context, ConversationAccessPort port, QuoteLines quote,
            Instant now) {
        // A-35: price only for the customer the conversation is bound to. A conversation without a
        // binding, or a session customer that differs, is refused before any ERP call (SEC-02); this
        // includes "no binding and no session customer" (security review 2 L-4).
        Optional<AuthenticatedCustomerRef> bound = port.boundCustomer(context.principal(), context.conversation());
        if (bound == null || bound.isEmpty() || !bound.get().equals(context.customer())) {
            throw ToolRejection.of(ToolErrorResult.Code.FORBIDDEN_RESOURCE);
        }
        List<QuoteRequest.Line> lines = requireOwnedVerifiedProducts(context, quote, now);
        QuoteRequest request;
        try {
            request = new QuoteRequest(context.conversation(), context.customer(), lines);
        } catch (IllegalArgumentException duplicateOrOutOfRange) {
            throw ToolRejection.of(ToolErrorResult.Code.TOOL_SCHEMA);
        }
        QuoteOutcome outcome;
        try {
            outcome = quotes.request(request);
        } catch (RuntimeException quoteFailure) {
            // QuoteService maps its own failures to NOT_QUOTED; anything left still hands off (arch M7).
            throw new ToolRejection(ToolErrorResult.Code.TOOL_UNAVAILABLE, true);
        }
        return switch (outcome) {
            case QuoteOutcome.Issued issued ->
                    new QuoteRequestResult(QuoteRequestResult.Status.ISSUED, issued.quoteHandle(), issued.lineCount());
            case QuoteOutcome.NotQuoted notQuoted ->
                    new QuoteRequestResult(QuoteRequestResult.Status.NOT_QUOTED, null, notQuoted.lineCount());
        };
    }

    private ToolResultDto search(ToolCallContext context, Supplier<List<CatalogMatch>> lookup, Instant now) {
        List<CatalogMatch> matches;
        try {
            matches = lookup.get();
        } catch (CatalogUnavailableException e) {
            // A-40(b) (owner 2026-11-19): an ERP outage hands off at once instead of letting the model retry
            // (rule 10: fail closed on outage, human fallback with a reason code).
            throw new ToolRejection(ToolErrorResult.Code.CATALOG_UNAVAILABLE, true);
        }
        List<ProductRef> candidates = new ArrayList<>();
        boolean filtered = false;
        for (CatalogMatch match : matches.stream().limit(ProductSearchResult.MAX_CANDIDATES).toList()) {
            Optional<ProductRef> candidate = toEgress(context, match, now);
            candidate.ifPresent(candidates::add);
            filtered |= candidate.isEmpty();
        }
        if (filtered) {
            invocations.recordFiltered(context, ToolName.SEARCH_VERIFIED_PRODUCT.wireName(), now);
            log.warn("tools.catalog_field_rejected tool={} turn={}", ToolName.SEARCH_VERIFIED_PRODUCT.wireName(),
                    context.turnId());
        }
        return new ProductSearchResult(candidates);
    }

    /**
     * Catalog text is untrusted (T-TOOL-06, T-TOOL-11): a candidate whose SKU, OE or category does not
     * match the egress format, or that carries a full VIN, is dropped whole. The productRef is issued
     * only for candidates that pass.
     */
    private Optional<ProductRef> toEgress(ToolCallContext context, CatalogMatch match, Instant now) {
        String sku = match.candidate().sku().value();
        String oe = match.candidate().oe();
        if (!CATALOG_CODE.matcher(sku).matches() || (oe != null && !CATALOG_CODE.matcher(oe).matches())
                || ArgumentParser.containsFullVin(sku) || ArgumentParser.containsFullVin(oe)
                || hasDigitRun(sku) || hasDigitRun(oe)) {
            return Optional.empty();
        }
        com.secureerp.agent.catalog.spi.Provenance provenance = match.candidate().provenance();
        Provenance egressProvenance;
        AvailabilityBucket availability;
        try {
            // Map and validate every field with a placeholder token first, so a bad row (including an
            // unknown enum value) is dropped on its own and no token is issued for it (security
            // review LOW-1, second review LOW-4).
            egressProvenance = Provenance.valueOf(provenance.name());
            availability = AvailabilityBucket.valueOf(match.availability().name());
            new ProductRef(PLACEHOLDER_REF, sku, oe, match.candidate().category(), egressProvenance, availability);
        } catch (RuntimeException invalidRow) {
            return Optional.empty();
        }
        String token = productRefs.issue(context.conversation(), sku, provenance, now);
        return Optional.of(new ProductRef(token, sku, oe, match.candidate().category(), egressProvenance,
                availability));
    }

    /** Seven or more digits, ignoring dashes: phone numbers, amounts or IDs posing as codes (LOW-2). */
    private static boolean hasDigitRun(String code) {
        return code != null && DIGIT_RUN.matcher(code.replace("-", "")).find();
    }
}
