package com.secureerp.agent;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SEC-05 positive control (EP-09c reviews, security M-2, backend M3): registered for every test class through
 * JUnit extension auto-detection, it logs {@link ArtifactScanner#CANARY} through SLF4J, the channel the
 * application logs use. {@code Sec05ArtifactScan} requires the canary in each scanned report directory before
 * it trusts "no findings": a build whose test output does not reach the scanned files fails instead of passing.
 */
public class LogCanaryExtension implements BeforeAllCallback {

    private static final Logger log = LoggerFactory.getLogger(LogCanaryExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        log.info(ArtifactScanner.CANARY);
    }
}
