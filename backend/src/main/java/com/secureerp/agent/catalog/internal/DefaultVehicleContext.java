package com.secureerp.agent.catalog.internal;

import com.secureerp.agent.catalog.VehicleContext;
import com.secureerp.agent.catalog.VinIntake;
import com.secureerp.agent.platform.ConversationId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Validates VINs at intake and keeps the conversation's current VIN (UC-02 AC-02.1). */
@Service
class DefaultVehicleContext implements VehicleContext {

    private static final Logger log = LoggerFactory.getLogger(DefaultVehicleContext.class);

    private final ConversationVinStore store;
    private final CatalogProperties properties;
    private final Clock clock;

    DefaultVehicleContext(ConversationVinStore store, CatalogProperties properties, Clock clock) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public VinIntake register(ConversationId conversation, String messageText) {
        Objects.requireNonNull(conversation, "conversation");
        VinExtractor.Extraction extraction = VinExtractor.extract(messageText);
        VinIntake intake = extraction.intake();
        try {
            Instant now = clock.instant();
            switch (intake) {
                case VinIntake.Accepted accepted ->
                        store.put(conversation, extraction.vin().orElseThrow(), now, now.plus(properties.vinTtl()));
                // A rejected VIN also retires the previous one, so no stale vehicle is searched.
                case VinIntake.Invalid invalid -> store.clear(conversation);
                case VinIntake.Multiple multiple -> store.clear(conversation);
                case VinIntake.None none -> {
                    // unchanged
                }
                case VinIntake.Unavailable unavailable -> throw new IllegalStateException("not an extractor outcome");
            }
            store.purgeExpired(now);
        } catch (RuntimeException storeFailure) {
            // A database error message can quote the row, VIN included: class name only (review L-6).
            log.warn("catalog.vin_intake outcome=Unavailable exception={}", storeFailure.getClass().getSimpleName());
            clearQuietly(conversation);
            return new VinIntake.Unavailable();
        }
        // Outcome name only: never the VIN, the WMI or the message (rule 10, logs).
        log.info("catalog.vin_intake outcome={}", intake.getClass().getSimpleName());
        return intake;
    }

    /** Best effort, so a VIN that could not be replaced is not searched later (review L-2). */
    private void clearQuietly(ConversationId conversation) {
        try {
            store.clear(conversation);
        } catch (RuntimeException alsoFailed) {
            log.warn("catalog.vin_intake clear_failed exception={}", alsoFailed.getClass().getSimpleName());
        }
    }
}
