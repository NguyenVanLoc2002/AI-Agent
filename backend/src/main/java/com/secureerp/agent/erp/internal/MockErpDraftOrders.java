package com.secureerp.agent.erp.internal;

import com.secureerp.agent.order.spi.DraftOrderCommand;
import com.secureerp.agent.order.spi.DraftOrderOutcome;
import com.secureerp.agent.order.spi.DraftOrderPort;
import com.secureerp.agent.order.spi.DraftOrderStatus;
import com.secureerp.agent.order.spi.ExternalRef;
import com.secureerp.agent.order.spi.IdempotencyKey;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Draft orders in the Mock ERP (ERP_CONNECTOR §3.6, §5; EP-07). Not a real ERP API (D-01..D-03).
 *
 * <p>Boundary rules (rule 20, T-ERP-02..04):
 * <ul>
 *   <li>one call, no retry; any exception from the backend, including a timeout, is
 *       {@link DraftOrderOutcome.Unknown} because the ERP may have written the order;</li>
 *   <li>only {@code draftNo} and {@code error.code} are read from the answer; the customer key,
 *       balances, costs, names and the error message are dropped here;</li>
 *   <li>only allowlisted error codes become {@link DraftOrderOutcome.Rejected}; an unlisted code or an
 *       answer of an unexpected shape is {@code Unknown};</li>
 *   <li>log lines name the exception class or a fixed code, never a message or a reference.</li>
 * </ul>
 * Whether the idempotency key is sent and whether the lookup is used is decided by order from static
 * configuration, not here (T-ERP-05).
 */
@Component
public class MockErpDraftOrders implements DraftOrderPort {

    private static final Logger log = LoggerFactory.getLogger(MockErpDraftOrders.class);

    private final MockErpBackend backend;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public MockErpDraftOrders(MockErpConnector connector) {
        this.backend = connector.backend();
    }

    @Override
    public DraftOrderOutcome create(DraftOrderCommand command, Optional<IdempotencyKey> idempotencyKey) {
        String raw;
        try {
            raw = backend.createDraftOrder(command.customer().value(), command.externalRef().value(),
                    idempotencyKey.map(IdempotencyKey::value).orElse(null), command.lines().size());
        } catch (RuntimeException e) {
            boolean timeout = e instanceof UncheckedIOException io && io.getCause() instanceof SocketTimeoutException;
            log.warn("erp.order.outcome_unknown exception={}", e.getClass().getSimpleName());
            return new DraftOrderOutcome.Unknown(timeout ? DraftOrderOutcome.UnknownReason.TIMEOUT
                    : DraftOrderOutcome.UnknownReason.UNCLASSIFIED);
        }
        try {
            JsonNode node = mapper.readTree(raw);
            JsonNode error = node == null ? null : node.get("error");
            if (error != null) {
                return rejection(error);
            }
            return new DraftOrderOutcome.Created(draftNo(node));
        } catch (JacksonException | IllegalArgumentException | NullPointerException e) {
            // The ERP answered, so it may have written the order.
            log.warn("erp.order.answer_unreadable");
            return new DraftOrderOutcome.Unknown(DraftOrderOutcome.UnknownReason.UNCLASSIFIED);
        }
    }

    private static DraftOrderOutcome rejection(JsonNode error) {
        JsonNode code = error.get("code");
        String value = code != null && code.isString() ? code.stringValue() : "";
        return switch (value) {
            case "CREDIT_HOLD", "UNKNOWN_CUSTOMER" ->
                    new DraftOrderOutcome.Rejected(DraftOrderOutcome.Rejection.CUSTOMER_NOT_ALLOWED);
            case "INVALID_ORDER" -> new DraftOrderOutcome.Rejected(DraftOrderOutcome.Rejection.INVALID_ORDER);
            default -> {
                log.warn("erp.order.error_unlisted");
                yield new DraftOrderOutcome.Unknown(DraftOrderOutcome.UnknownReason.UNCLASSIFIED);
            }
        };
    }

    @Override
    public Optional<DraftOrderStatus> findByExternalRef(ExternalRef externalRef) {
        Optional<String> raw = backend.fetchDraftByExternalRef(externalRef.value());
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new DraftOrderStatus(draftNo(mapper.readTree(raw.get()))));
        } catch (JacksonException | IllegalArgumentException | NullPointerException e) {
            log.warn("erp.order.row_skipped");
            return Optional.empty();
        }
    }

    private static String draftNo(JsonNode node) {
        JsonNode value = node.get("draftNo");
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException("missing or non-string draft number");
        }
        return value.stringValue();
    }
}
