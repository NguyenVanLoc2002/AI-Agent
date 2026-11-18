package com.secureerp.agent.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.catalog.VinIntake;
import com.secureerp.agent.catalog.spi.Vin;
import com.secureerp.agent.platform.ConversationId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Intake updates the conversation's current VIN (UC-02 AC-02.1); PostgreSQL version: {@code VinSearchIT}. */
@ExtendWith(OutputCaptureExtension.class)
class DefaultVehicleContextTest {

    private static final Instant NOW = Instant.parse("2026-11-18T08:00:00Z");
    private static final ConversationId CONVERSATION = new ConversationId("cv_VinConvAAAAAAAAAAAAAAA");
    private static final String VIN = "5ZZAPAS11RA000001";
    private static final String OTHER_VIN = "RLZAPAS20RA000002";

    private final InMemoryStore store = new InMemoryStore();
    private final DefaultVehicleContext context = new DefaultVehicleContext(store,
            new CatalogProperties(Duration.ofHours(2)), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptedVinIsStoredWithTheTtlAndReplacedByANewerOne(CapturedOutput output) {
        assertThat(context.register(CONVERSATION, "VIN " + VIN)).isInstanceOf(VinIntake.Accepted.class);
        assertThat(store.current(CONVERSATION, NOW)).contains(new Vin(VIN));
        assertThat(store.expiresAt.get(CONVERSATION)).isEqualTo(NOW.plus(Duration.ofHours(2)));
        assertThat(store.current(CONVERSATION, NOW.plus(Duration.ofHours(2)))).isEmpty();

        context.register(CONVERSATION, "xe khac: " + OTHER_VIN);
        assertThat(store.current(CONVERSATION, NOW)).contains(new Vin(OTHER_VIN));

        assertThat(output.getAll()).doesNotContain(VIN, OTHER_VIN, "5ZZ", "RLZ")
                .contains("catalog.vin_intake outcome=Accepted");
    }

    @Test
    void aMessageWithoutVinKeepsTheCurrentVin() {
        context.register(CONVERSATION, VIN);

        assertThat(context.register(CONVERSATION, "can ma phanh truoc")).isEqualTo(new VinIntake.None());
        assertThat(store.current(CONVERSATION, NOW)).contains(new Vin(VIN));
    }

    @Test
    void anInvalidVinClearsTheCurrentVin(CapturedOutput output) {
        context.register(CONVERSATION, VIN);

        assertThat(context.register(CONVERSATION, "VIN 5ZZAPAS10RA000001")).isEqualTo(new VinIntake.Invalid());
        assertThat(store.current(CONVERSATION, NOW)).isEmpty();
        assertThat(output.getAll()).doesNotContain("5ZZAPAS10RA000001");
    }

    @Test
    void twoVinsClearTheCurrentVin() {
        context.register(CONVERSATION, VIN);

        assertThat(context.register(CONVERSATION, VIN + " " + OTHER_VIN)).isEqualTo(new VinIntake.Multiple());
        assertThat(store.current(CONVERSATION, NOW)).isEmpty();
    }

    @Test
    void theVinIsBoundToItsConversation() {
        context.register(CONVERSATION, VIN);

        assertThat(store.current(new ConversationId("cv_OtherConvAAAAAAAAAAAAA"), NOW)).isEmpty();
    }

    /** Reviews L-2/L2/L-6: a store failure is a typed outcome, the old VIN is cleared, the log has no VIN. */
    @Test
    void storeFailureIsUnavailableAndRetiresTheOldVin(CapturedOutput output) {
        context.register(CONVERSATION, VIN);
        store.failPutWith = new IllegalStateException("Failing row contains (" + OTHER_VIN + ")");

        assertThat(context.register(CONVERSATION, OTHER_VIN)).isEqualTo(new VinIntake.Unavailable());
        assertThat(store.current(CONVERSATION, NOW)).isEmpty();
        assertThat(output.getAll()).doesNotContain(VIN, OTHER_VIN)
                .contains("catalog.vin_intake outcome=Unavailable exception=IllegalStateException");
    }

    @Test
    void storeFailureOnClearIsAlsoUnavailable(CapturedOutput output) {
        store.failClearWith = new IllegalStateException("db down");

        assertThat(context.register(CONVERSATION, "VIN 5ZZAPAS10RA000001")).isEqualTo(new VinIntake.Unavailable());
        assertThat(output.getAll()).contains("catalog.vin_intake clear_failed");
    }

    @Test
    void everyIntakePurgesExpiredRows() {
        context.register(CONVERSATION, "khong co vin");

        assertThat(store.purgedAt).containsExactly(NOW);
    }

    private static final class InMemoryStore implements ConversationVinStore {

        private final Map<ConversationId, Vin> vins = new HashMap<>();
        private final Map<ConversationId, Instant> expiresAt = new HashMap<>();
        private final List<Instant> purgedAt = new ArrayList<>();
        private RuntimeException failPutWith;
        private RuntimeException failClearWith;

        @Override
        public void purgeExpired(Instant now) {
            purgedAt.add(now);
        }

        @Override
        public void put(ConversationId conversation, Vin vin, Instant registeredAt, Instant expires) {
            if (failPutWith != null) {
                throw failPutWith;
            }
            vins.put(conversation, vin);
            expiresAt.put(conversation, expires);
        }

        @Override
        public void clear(ConversationId conversation) {
            if (failClearWith != null) {
                throw failClearWith;
            }
            vins.remove(conversation);
            expiresAt.remove(conversation);
        }

        @Override
        public Optional<Vin> current(ConversationId conversation, Instant now) {
            Instant expires = expiresAt.get(conversation);
            return expires != null && expires.isAfter(now) ? Optional.of(vins.get(conversation)) : Optional.empty();
        }
    }
}
