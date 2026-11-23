package com.secureerp.agent.gateway.internal;

import com.secureerp.agent.audit.EgressAuditLog;
import com.secureerp.agent.audit.EgressDecisionEvent;
import com.secureerp.agent.gateway.AiGateway;
import com.secureerp.agent.gateway.GatewayResult;
import com.secureerp.agent.gateway.GatewayResult.FailureReason;
import com.secureerp.agent.gateway.GatewayResult.RefusalReason;
import com.secureerp.agent.gateway.api.ModelRequest;
import com.secureerp.agent.gateway.api.ModelResponse;
import com.secureerp.agent.gateway.internal.ModelProviderPort.ProviderReply;
import com.secureerp.agent.gateway.internal.ModelProviderPort.ProviderTransportException;
import com.secureerp.agent.platform.KillSwitch;
import com.secureerp.agent.policy.EgressPolicy;
import com.secureerp.agent.policy.EgressVerdict;
import com.secureerp.agent.policy.PolicyCode;
import com.secureerp.agent.policy.ResponseScreen;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Egress pipeline (AI_GATEWAY §2, rule 30 §3, ADR-003/004/016):
 * <ol>
 *   <li>type gate: only {@link ModelRequest} is accepted (ARCH-05/06 at build time);</li>
 *   <li>serialize with the gateway-private mapper;</li>
 *   <li>structure check: every property name must be allowlisted;</li>
 *   <li>final-bytes scan by {@link EgressPolicy};</li>
 *   <li>audit the decision through {@link EgressAuditLog} (a direct call, not an event: a missing
 *       audit bean fails startup); if the audit fails, nothing is sent;</li>
 *   <li>PASS only: send the exact scanned bytes; retry at most once on timeout/I-O/5xx, with the
 *       same byte array (no re-serialization, so a retry cannot carry different content). Every
 *       attempt, the retry included, is audited before it is sent.</li>
 * </ol>
 * Fail-closed throughout: any exception before sending is a refusal. Logs carry trace ID, decision,
 * policy code, field path and lengths only.
 *
 * <p>Call it outside a database transaction: the audit write uses its own transaction and needs a
 * second pooled connection otherwise (second review L6).
 *
 * <p>EP-09a (ADR-013, A-44): the kill switch is checked before anything is serialized and again before every
 * provider attempt, the retry included (SEC-06); a 401/403 opens the provider-auth breaker, which only an
 * operator closes (SEC-07). Quotas: EP-03.
 */
@Service
public class DefaultAiGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiGateway.class);

    /** One initial attempt plus one retry (rule 30 §7: bounded retries). */
    static final int MAX_ATTEMPTS = 2;

    /** Policy code of the egress row when the response screen itself failed (A-59). */
    static final String SCREEN_FAILED = "SCREEN_FAILED";

    private final EgressPolicy policy;
    private final ResponseScreen responseScreen;
    private final ModelProviderPort provider;
    private final EgressAuditLog auditLog;
    private final Clock clock;
    private final KillSwitch killSwitch;

    public DefaultAiGateway(EgressPolicy policy, ResponseScreen responseScreen, ModelProviderPort provider,
            EgressAuditLog auditLog, Clock clock, KillSwitch killSwitch) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.responseScreen = Objects.requireNonNull(responseScreen, "responseScreen");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.killSwitch = Objects.requireNonNull(killSwitch, "killSwitch");
    }

    /** The refusal when the AI is off right now, or empty; a failing switch is off (fail closed). */
    private Optional<GatewayResult> switchedOff(ModelRequest request) {
        KillSwitch.State state;
        try {
            state = Objects.requireNonNull(killSwitch.current(), "state");
        } catch (RuntimeException e) {
            state = KillSwitch.State.unreadable();
        }
        if (state.aiEnabled()) {
            return Optional.empty();
        }
        log.warn("gateway.egress decision=SWITCHED_OFF reason={} traceId={}", state.disabledReason(),
                request.traceId());
        return Optional.of(new GatewayResult.Refused(state.disabledReason() == KillSwitch.Reason.PROVIDER_AUTH
                ? RefusalReason.PROVIDER_AUTH_BREAKER : RefusalReason.AI_DISABLED));
    }

    @Override
    public GatewayResult complete(ModelRequest request) {
        Objects.requireNonNull(request, "request");
        Optional<GatewayResult> off = switchedOff(request);
        if (off.isPresent()) {
            return off.get();
        }
        byte[] body;
        EgressVerdict verdict;
        List<String> fieldNames = List.of();
        try {
            body = EgressSerializer.serialize(request);
            Optional<EgressSerializer.Structure> structure = EgressSerializer.structure(body);
            if (structure.isEmpty()) {
                verdict = EgressVerdict.block(PolicyCode.UNCLASSIFIED_FIELD, null, "$");
            } else {
                fieldNames = structure.get().fieldNames();
                verdict = Objects.requireNonNull(policy.inspect(structure.get().payload()), "verdict");
            }
        } catch (RuntimeException | StackOverflowError e) {
            // A stack overflow in a pattern scan is a refusal with an audit row too (third review L-1).
            log.warn("gateway.egress decision=BLOCK policy=MALFORMED_PAYLOAD traceId={} exception={}",
                    request.traceId(), e.getClass().getSimpleName());
            return audit(request, EgressVerdict.block(PolicyCode.MALFORMED_PAYLOAD, null, "$"), List.of(), 0, 0)
                    ? new GatewayResult.Refused(RefusalReason.DLP_EGRESS)
                    : new GatewayResult.Refused(RefusalReason.AUDIT_UNAVAILABLE);
        }

        if (!verdict.allowed()) {
            if (!audit(request, verdict, fieldNames, body.length, 0)) {
                return new GatewayResult.Refused(RefusalReason.AUDIT_UNAVAILABLE);
            }
            log.warn("gateway.egress decision=BLOCK policy={} field={} traceId={} bytes={}",
                    verdict.policyCode(), verdict.fieldPath(), request.traceId(), body.length);
            return new GatewayResult.Refused(RefusalReason.DLP_EGRESS);
        }
        log.info("gateway.egress decision=PASS traceId={} bytes={} fields={}", request.traceId(), body.length,
                fieldNames.size());
        return send(request, body, verdict, fieldNames);
    }

    private boolean audit(ModelRequest request, EgressVerdict verdict, List<String> fieldNames, int length,
            int attempt) {
        try {
            auditLog.record(new EgressDecisionEvent(request.traceId(),
                    verdict.allowed() ? EgressDecisionEvent.Decision.PASS : EgressDecisionEvent.Decision.BLOCK,
                    verdict.allowed() ? null : verdict.policyCode().name(), verdict.honeytokenId(),
                    verdict.fieldPath(), fieldNames, length, clock.instant(), attempt,
                    EgressDecisionEvent.Direction.REQUEST));
            return true;
        } catch (RuntimeException e) {
            log.error("gateway.egress audit_failed traceId={} exception={}", request.traceId(),
                    e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * SEC03-F3 (A-58): a honeytoken in the answer withholds it. Fails closed: a screen that throws withholds too.
     * SEC03-F3-AUDIT (A-59): the withheld answer is recorded as an egress row with direction RESPONSE (policy code,
     * honeytoken ID, structural path, the attempt that produced it; never the answer). The answer stays withheld when
     * that record fails (decision 3A): the record documents the block, it does not authorize anything.
     */
    private GatewayResult screened(ModelRequest request, ModelResponse response, int attempt, int byteLength) {
        List<String> values = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        values.add(response.text());
        paths.add("$.text");
        for (int i = 0; i < response.toolProposals().size(); i++) {
            values.add(response.toolProposals().get(i).name());
            paths.add("$.toolProposals[" + i + "].name");
            values.add(response.toolProposals().get(i).argumentsJson());
            paths.add("$.toolProposals[" + i + "].argumentsJson");
        }
        Optional<ResponseScreen.Hit> hit;
        try {
            hit = Objects.requireNonNull(responseScreen.honeytokenIn(values), "screen");
        } catch (RuntimeException e) {
            log.warn("gateway.response screen_failed traceId={} exception={}", request.traceId(),
                    e.getClass().getSimpleName());
            auditWithheld(request, SCREEN_FAILED, null, "$", response, attempt, byteLength);
            return new GatewayResult.Refused(RefusalReason.OUTPUT_HONEYTOKEN);
        }
        if (hit.isPresent() && hit.get().valueIndex() >= paths.size()) {
            // A screen breaking its contract (A-59 review L1): withheld and recorded as a screen failure.
            log.warn("gateway.response screen_failed traceId={} exception=IndexOutOfRange", request.traceId());
            auditWithheld(request, SCREEN_FAILED, null, "$", response, attempt, byteLength);
            return new GatewayResult.Refused(RefusalReason.OUTPUT_HONEYTOKEN);
        }
        if (hit.isPresent()) {
            String path = hit.get().valueIndex() < 0 ? "$" : paths.get(hit.get().valueIndex());
            log.warn("gateway.response decision=BLOCK policy=HONEYTOKEN honeytokenId={} traceId={}",
                    hit.get().honeytokenId(), request.traceId());
            auditWithheld(request, PolicyCode.HONEYTOKEN.name(), hit.get().honeytokenId(), path, response, attempt,
                    byteLength);
            return new GatewayResult.Refused(RefusalReason.OUTPUT_HONEYTOKEN);
        }
        return new GatewayResult.Completed(response);
    }

    private void auditWithheld(ModelRequest request, String policyCode, String honeytokenId, String path,
            ModelResponse response, int attempt, int byteLength) {
        List<String> fieldNames = new ArrayList<>();
        if (response.text() != null) {
            fieldNames.add("text");
        }
        if (!response.toolProposals().isEmpty()) {
            fieldNames.addAll(List.of("toolProposals", "name", "argumentsJson"));
        }
        try {
            auditLog.record(new EgressDecisionEvent(request.traceId(), EgressDecisionEvent.Decision.BLOCK, policyCode,
                    honeytokenId, path, fieldNames, byteLength, clock.instant(), attempt,
                    EgressDecisionEvent.Direction.RESPONSE));
        } catch (RuntimeException e) {
            log.error("gateway.response audit_failed traceId={} exception={}", request.traceId(),
                    e.getClass().getSimpleName());
        }
    }

    private GatewayResult send(ModelRequest request, byte[] body, EgressVerdict verdict, List<String> fieldNames) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            // ADR-013: checked before every provider call, the retry included (nothing audited as sent).
            Optional<GatewayResult> off = switchedOff(request);
            if (off.isPresent()) {
                return off.get();
            }
            if (!audit(request, verdict, fieldNames, body.length, attempt)) {
                return new GatewayResult.Refused(RefusalReason.AUDIT_UNAVAILABLE);
            }
            try {
                ProviderReply reply = provider.send(body, request.budget());
                int status = reply.status();
                if (status >= 200 && status < 300) {
                    Optional<ModelResponse> response = EgressSerializer.readResponse(reply.body());
                    if (response.isPresent()) {
                        return screened(request, response.get(), attempt, reply.body().length);
                    }
                    log.warn("gateway.provider invalid_response traceId={} bytes={}", request.traceId(),
                            reply.body() == null ? 0 : reply.body().length);
                    return new GatewayResult.Failed(FailureReason.INVALID_PROVIDER_RESPONSE);
                }
                log.warn("gateway.provider status={} attempt={} traceId={}", status, attempt, request.traceId());
                if (status == 401 || status == 403) {
                    // SEC-07: the key is revoked or invalid; stop every further call until an operator acts.
                    try {
                        killSwitch.tripProviderAuth();
                    } catch (RuntimeException e) {
                        // The contract says it never throws; the answer stays PROVIDER_AUTH either way.
                        log.error("gateway.provider breaker_trip_failed exception={}", e.getClass().getSimpleName());
                    }
                    return new GatewayResult.Failed(FailureReason.PROVIDER_AUTH);
                }
                if (status < 500) {
                    return new GatewayResult.Failed(FailureReason.PROVIDER_REJECTED);
                }
            } catch (ProviderTransportException e) {
                log.warn("gateway.provider transport={} attempt={} traceId={}", e.kind(), attempt, request.traceId());
                if (e.kind() == ProviderTransportException.Kind.NOT_CONFIGURED
                        || e.kind() == ProviderTransportException.Kind.CANCELLED) {
                    return new GatewayResult.Failed(FailureReason.PROVIDER_UNAVAILABLE);
                }
                if (e.kind() == ProviderTransportException.Kind.RESPONSE_TOO_LARGE) {
                    return new GatewayResult.Failed(FailureReason.INVALID_PROVIDER_RESPONSE);
                }
            } catch (RuntimeException e) {
                // Every outcome is a reason code (security review L1); no message, no retry.
                log.warn("gateway.provider unexpected exception={} attempt={} traceId={}",
                        e.getClass().getSimpleName(), attempt, request.traceId());
                return new GatewayResult.Failed(FailureReason.PROVIDER_UNAVAILABLE);
            }
        }
        return new GatewayResult.Failed(FailureReason.PROVIDER_UNAVAILABLE);
    }
}
