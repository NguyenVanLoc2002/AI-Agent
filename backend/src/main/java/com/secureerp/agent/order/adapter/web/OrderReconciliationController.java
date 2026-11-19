package com.secureerp.agent.order.adapter.web;

import com.secureerp.agent.identity.CurrentIdentity;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.order.OrderReconciliation;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff reconciliation of unknown ERP outcomes (SYSTEM_ARCHITECTURE §8, ADR-009). Transport only:
 * the staff identity comes from the session and the role is checked by the service (403 for anyone
 * but SALES). Registered with profile {@code demo} only, because the only users are the synthetic
 * demo users (ADR-010); the request body is capped by the demo request size filter.
 */
@RestController
@Profile("demo")
@RequestMapping(path = "/api/internal/orders", produces = MediaType.APPLICATION_JSON_VALUE)
class OrderReconciliationController {

    private final CurrentIdentity identity;
    private final OrderReconciliation reconciliation;

    OrderReconciliationController(CurrentIdentity identity, OrderReconciliation reconciliation) {
        this.identity = identity;
        this.reconciliation = reconciliation;
    }

    @GetMapping("/unknown")
    List<UnknownView> unknown() {
        return reconciliation.unknown(session()).stream()
                .map(order -> new UnknownView(order.requestId(), order.quoteRef(), order.quoteVersion(),
                        order.externalRef(), order.since() == null ? null : order.since().toString(),
                        order.reportedErpOrderRef()))
                .toList();
    }

    @PostMapping(path = "/{requestId}/reconcile", consumes = MediaType.APPLICATION_JSON_VALUE)
    ReconciledView reconcile(@PathVariable long requestId, @Valid @RequestBody ReconcileRequest request) {
        return new ReconciledView(requestId, reconciliation.reconcile(session(), requestId, request.outcome(),
                request.erpOrderRef()).name());
    }

    private SessionIdentity session() {
        return identity.current().orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    record ReconcileRequest(@NotNull OrderReconciliation.Finding outcome,
            @Pattern(regexp = "^[A-Za-z0-9:_.-]{1,64}$") String erpOrderRef) {
    }

    /** {@code reportedErpOrderRef}: what the ERP answered after the timeout, when it did (else null). */
    record UnknownView(long requestId, String quoteRef, int quoteVersion, String externalRef, String since,
            String reportedErpOrderRef) {
    }

    record ReconciledView(long requestId, String status) {
    }
}
