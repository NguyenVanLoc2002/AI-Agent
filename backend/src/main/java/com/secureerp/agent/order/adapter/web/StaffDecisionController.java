package com.secureerp.agent.order.adapter.web;

import com.secureerp.agent.identity.CurrentIdentity;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.order.StaffDecisions;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
 * Staff decision on created draft orders (EP-08b). Transport only: identity from the session, role and
 * team checked by the service. Profile {@code demo} only (A-41(j)); {@code /api/internal/**} is SALES
 * only at the URL level as well (ADR-015, EP-08a); the body is capped by the request size filter.
 */
@RestController
@Profile("demo")
@RequestMapping(path = "/api/internal/orders", produces = MediaType.APPLICATION_JSON_VALUE)
class StaffDecisionController {

    private final CurrentIdentity identity;
    private final StaffDecisions decisions;

    StaffDecisionController(CurrentIdentity identity, StaffDecisions decisions) {
        this.identity = identity;
        this.decisions = decisions;
    }

    @GetMapping("/awaiting-decision")
    List<AwaitingView> awaiting() {
        return decisions.awaiting(session()).stream()
                .map(order -> new AwaitingView(order.requestId(), order.quoteRef(), order.quoteVersion(),
                        order.erpOrderRef(), order.since() == null ? null : order.since().toString()))
                .toList();
    }

    @PostMapping(path = "/{requestId}/staff-decision", consumes = MediaType.APPLICATION_JSON_VALUE)
    DecidedView decide(@PathVariable long requestId, @Valid @RequestBody DecisionRequest request) {
        return new DecidedView(requestId, decisions.decide(session(), requestId, request.decision()).name());
    }

    private SessionIdentity session() {
        return identity.current().orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    /** Only the decision: no free text (owner decision 2026-11-20, A-43). */
    record DecisionRequest(@NotNull StaffDecisions.Decision decision) {
    }

    record AwaitingView(long requestId, String quoteRef, int quoteVersion, String erpOrderRef, String since) {
    }

    record DecidedView(long requestId, String status) {
    }
}
