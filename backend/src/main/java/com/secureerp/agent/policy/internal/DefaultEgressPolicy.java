package com.secureerp.agent.policy.internal;

import com.secureerp.agent.policy.EgressPayload;
import com.secureerp.agent.policy.EgressPolicy;
import com.secureerp.agent.policy.EgressVerdict;
import com.secureerp.agent.policy.PolicyCode;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Final-bytes scan (AI_GATEWAY §2.3–2.4): each string value (honeytokens + patterns), the raw
 * payload (honeytokens), then all values together (sentinels split across fields or messages: their
 * digits concatenated for digit tokens, an assembly of their look-alike skeletons in any order for
 * text tokens, SEC03-F4 / A-57).
 *
 * <p>Server-generated identifiers (trace ID, prompt ID, opaque refs and handles, masked VIN) are
 * format-validated by their egress record and contain random characters, so they get the honeytoken
 * check only, and their digits are left out of the split projection (security review M3: random
 * refs produced FULL_VIN / PHONE / split-digit false positives). SKU and OE remain fully scanned
 * because their content comes from the ERP.
 *
 * <p>The exemption is keyed by field name, so it is safe only while every egress field with one of
 * these names is strictly validated: ARCH-05d allows them only on approved {@code Type.field} pairs,
 * and the opaque refs must be 22-character tokens without digit runs (second review M2/M4).
 *
 * <p>Not implemented in SEC-01a: the per-conversation sliding window across separate requests. It
 * needs a conversation key that only exists with the agent loop (EP-04); recorded for SEC-01b.
 */
@Component
public class DefaultEgressPolicy implements EgressPolicy {

    /** Upper bound of a provider request body (DESIGN PROPOSAL, SEC-01a). */
    public static final int MAX_PAYLOAD_BYTES = 64 * 1024;

    public static final Set<String> SERVER_IDENTIFIER_FIELDS =
            Set.of("traceId", "systemPromptId", "productRef", "vinRef", "quoteHandle", "maskedVin");

    private final SensitiveDataDetector detector;

    public DefaultEgressPolicy(SensitiveDataDetector detector) {
        this.detector = detector;
    }

    @Override
    public EgressVerdict inspect(EgressPayload payload) {
        if (payload.rawText().getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            return EgressVerdict.block(PolicyCode.PAYLOAD_TOO_LARGE, null, "$");
        }
        for (EgressPayload.Segment segment : payload.segments()) {
            Optional<SensitiveDataDetector.Finding> finding = isServerIdentifier(segment)
                    ? detector.honeytoken(segment.value())
                    : detector.detect(segment.value());
            if (finding.isPresent()) {
                return block(finding.get(), segment.fieldPath());
            }
        }
        Optional<SensitiveDataDetector.Finding> raw = detector.honeytokenInRawPayload(payload.rawText());
        if (raw.isPresent()) {
            return block(raw.get(), "$");
        }
        Optional<SensitiveDataDetector.Finding> split = detector.splitHoneytoken(payload.segments().stream()
                .filter(segment -> !isServerIdentifier(segment))
                .map(EgressPayload.Segment::value)
                .toList());
        return split.map(f -> block(f, "$")).orElseGet(EgressVerdict::pass);
    }

    static boolean isServerIdentifier(EgressPayload.Segment segment) {
        String path = segment.fieldPath();
        String last = path.substring(path.lastIndexOf('.') + 1);
        return SERVER_IDENTIFIER_FIELDS.contains(last);
    }

    private static EgressVerdict block(SensitiveDataDetector.Finding finding, String fieldPath) {
        return EgressVerdict.block(finding.code(), finding.honeytokenId(), fieldPath);
    }
}
