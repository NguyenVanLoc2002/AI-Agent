package com.secureerp.agent.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.catalog.CatalogMatch;
import com.secureerp.agent.catalog.spi.AvailabilityBucket;
import com.secureerp.agent.catalog.spi.ExactProductQuery;
import com.secureerp.agent.catalog.spi.ProductCandidate;
import com.secureerp.agent.catalog.spi.Provenance;
import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.catalog.spi.VerifiedProductPort;
import com.secureerp.agent.catalog.spi.Vin;
import com.secureerp.agent.platform.ConversationId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Catalog-side VIN rules (EP-05 architecture review M1, M2, L6), independent of the adapter. */
class DefaultCatalogSearchTest {

    private static final Instant NOW = Instant.parse("2026-11-18T08:00:00Z");
    private static final ConversationId CONVERSATION = new ConversationId("cv_CatalogConvAAAAAAAAAAA");
    private static final Vin VIN = new Vin("5ZZAPAS11RA000001");

    private final List<ExactProductQuery> queries = new ArrayList<>();

    /** An adapter that (wrongly) labels every row VERIFIED, also for a VIN query. */
    private final VerifiedProductPort verifiedEverything = query -> {
        queries.add(query);
        return List.of(new ProductCandidate(new Sku("SKU-BRK-PAD-A"), "04465-0K290", "Ma phanh truoc",
                Provenance.VERIFIED, "sku-brk-pad-a"));
    };

    private DefaultCatalogSearch search(Optional<Vin> current) {
        ConversationVinStore store = new ConversationVinStore() {
            @Override
            public void put(ConversationId conversation, Vin vin, Instant registeredAt, Instant expiresAt) {
            }

            @Override
            public void clear(ConversationId conversation) {
            }

            @Override
            public void purgeExpired(Instant now) {
            }

            @Override
            public Optional<Vin> current(ConversationId conversation, Instant now) {
                return conversation.equals(CONVERSATION) ? current : Optional.empty();
            }
        };
        return new DefaultCatalogSearch(verifiedEverything, sku -> AvailabilityBucket.CON_HANG, store,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void vinResultsAreAtMostCatalogLookupWhateverTheAdapterSays() {
        Optional<List<CatalogMatch>> matches = search(Optional.of(VIN)).searchByConversationVin(CONVERSATION);

        assertThat(matches).hasValueSatisfying(list -> assertThat(list).singleElement()
                .satisfies(m -> assertThat(m.candidate().provenance()).isEqualTo(Provenance.CATALOG_LOOKUP)));
        assertThat(queries).singleElement().isEqualTo(new ExactProductQuery.ByVin(VIN));
    }

    @Test
    void noCurrentVinMeansNoLookupAtAll() {
        assertThat(search(Optional.empty()).searchByConversationVin(CONVERSATION)).isEmpty();
        assertThat(queries).isEmpty();
    }

    @Test
    void callersCannotBuildAVinQueryThemselves() {
        DefaultCatalogSearch catalog = search(Optional.of(VIN));

        assertThatThrownBy(() -> catalog.search(new ExactProductQuery.ByVin(VIN)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(queries).isEmpty();
        // Other queries keep the adapter's label (OE search is unchanged; A-31 OPEN).
        assertThat(catalog.search(new ExactProductQuery.ByOe("04465-0K290")))
                .extracting(m -> m.candidate().provenance()).containsExactly(Provenance.VERIFIED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-PT1H", "PT24H1S", "P2D"})
    void vinTtlMustBePositiveAndAtMostADay(String ttl) {
        assertThatThrownBy(() -> new CatalogProperties(Duration.parse(ttl))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void vinTtlBounds() {
        assertThat(new CatalogProperties(Duration.ofHours(24)).vinTtl()).isEqualTo(CatalogProperties.MAX_VIN_TTL);
        assertThatThrownBy(() -> new CatalogProperties(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
