package com.secureerp.agent.policy.internal;

import com.secureerp.agent.policy.ResponseScreen;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * SEC03-F3 (A-58): honeytokens in a model answer ({@link SensitiveDataDetector#honeytokenInAnswer}): text tokens as
 * the egress scan sees them, digit tokens without the projections that turn numbered steps into a sentinel. The full
 * pattern scan is not applied: its money and VIN rules would reject part codes the agent allows.
 */
@Component
public class DefaultResponseScreen implements ResponseScreen {

    private final SensitiveDataDetector detector;

    public DefaultResponseScreen(SensitiveDataDetector detector) {
        this.detector = Objects.requireNonNull(detector, "detector");
    }

    @Override
    public Optional<String> honeytokenIn(List<String> values) {
        List<String> present = values.stream().filter(Objects::nonNull).toList();
        return detector.honeytokenInAnswer(present).map(SensitiveDataDetector.Finding::honeytokenId);
    }
}
