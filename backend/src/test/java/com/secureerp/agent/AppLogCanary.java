package com.secureerp.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * SEC-05 positive control for the application log (EP-09c round-2 review M1). A test-only component, picked up by
 * the component scan of every test application context: once Spring Boot has set up logging it logs
 * {@link ArtifactScanner#APP_CANARY} through an application logger, in the structured format the application
 * uses. {@code Sec05ArtifactScan} requires that line in the failsafe reports.
 */
@Component
class AppLogCanary {

    private static final Logger log = LoggerFactory.getLogger(AppLogCanary.class);

    @EventListener(ApplicationReadyEvent.class)
    void ready() {
        log.info(ArtifactScanner.APP_CANARY);
    }
}
