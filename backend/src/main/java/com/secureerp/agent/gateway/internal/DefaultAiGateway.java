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
import com.secureerp.agent.policy.EgressPolicy;
import com.secureerp.agent.policy.EgressVerdict;
import com.secureerp.agent.policy.PolicyCode;
import java.time.Clock;
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
 * <p>Not in SEC-01a: kill switch and circuit breaker (ADR-013, EP-09); quotas (EP-03).
 */
@Service
public class DefaultAiGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiGateway.class);

    /** One initial attempt plus one retry (rule 30 §7: bounded retries). */
    static final int MAX_ATTEMPTS = 2;

    private final EgressPolicy policy;
    private final ModelProviderPort provider;
    private final EgressAuditLog auditLog;
    private final Clock clock;

    public DefaultAiGateway(EgressPolicy policy, ModelProviderPort provider, EgressAuditLog auditLog, Clock clock) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public GatewayResult complete(ModelRequest request) {
        Objects.requireNonNull(request, "request");
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
                    verdict.fieldPath(), fieldNames, length, clock.instant(), attempt));
            return true;
        } catch (RuntimeException e) {
            log.error("gateway.egress audit_failed traceId={} exception={}", request.traceId(),
                    e.getClass().getSimpleName());
            return false;
        }
    }

    private GatewayResult send(ModelRequest request, byte[] body, EgressVerdict verdict, List<String> fieldNames) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (!audit(request, verdict, fieldNames, body.length, attempt)) {
                return new GatewayResult.Refused(RefusalReason.AUDIT_UNAVAILABLE);
            }
            try {
                ProviderReply reply = provider.send(body, request.budget());
                int status = reply.status();
                if (status >= 200 && status < 300) {
                    Optional<ModelResponse> response = EgressSerializer.readResponse(reply.body());
                    if (response.isPresent()) {
                        return new GatewayResult.Completed(response.get());
                    }
                    log.warn("gateway.provider invalid_response traceId={} bytes={}", request.traceId(),
                            reply.body() == null ? 0 : reply.body().length);
                    return new GatewayResult.Failed(FailureReason.INVALID_PROVIDER_RESPONSE);
                }
                log.warn("gateway.provider status={} attempt={} traceId={}", status, attempt, request.traceId());
                if (status == 401 || status == 403) {
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
