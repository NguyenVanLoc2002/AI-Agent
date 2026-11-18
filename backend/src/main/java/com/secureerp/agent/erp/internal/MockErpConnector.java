package com.secureerp.agent.erp.internal;

import com.secureerp.agent.catalog.spi.AvailabilityBucket;
import com.secureerp.agent.catalog.spi.AvailabilityBucketPort;
import com.secureerp.agent.catalog.spi.CatalogUnavailableException;
import com.secureerp.agent.catalog.spi.ExactProductQuery;
import com.secureerp.agent.catalog.spi.ProductCandidate;
import com.secureerp.agent.catalog.spi.Provenance;
import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.catalog.spi.VerifiedProductPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mock ERP connector for the PoC (target name {@code MockErpConnector}, ERP_CONNECTOR §5; ADR-005
 * and A-07 ACCEPTED/APPROVED 2026-11-18). Implements the catalog ports only; pricing, credit and
 * draft orders come with their own issues.
 *
 * <p>Boundary rules (rule 20, T-ERP-02/03):
 * <ul>
 *   <li>only allowlisted keys are read from an ERP row; every other field is dropped here and
 *       never reaches a DTO, a log line or an exception;</li>
 *   <li>a row with an unexpected shape is skipped, never partially used;</li>
 *   <li>a backend failure becomes {@link CatalogUnavailableException} (code only); the log line
 *       names the exception class, never its message.</li>
 * </ul>
 */
@Component
public class MockErpConnector implements VerifiedProductPort, AvailabilityBucketPort {

    private static final Logger log = LoggerFactory.getLogger(MockErpConnector.class);

    /** Bucket threshold: DESIGN PROPOSAL (A-12); not derived from any real ERP. */
    static final int LOW_STOCK_THRESHOLD = 3;

    /**
     * Provenance is a property of the source, assigned by the adapter from its configuration, never
     * read from row content (architecture review M7): the synthetic Mock catalog is the PoC's
     * verified source. Whether a real catalog counts as VERIFIED is OPEN (D-05).
     */
    static final Provenance SOURCE_PROVENANCE = Provenance.VERIFIED;

    private final MockErpBackend backend;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public MockErpConnector(@Value("${apas.erp.mock.fault-mode:NONE}") MockErpFaultMode faultMode) {
        this.backend = new MockErpBackend(faultMode);
    }

    @Override
    public List<ProductCandidate> search(ExactProductQuery query) {
        Map<String, ProductCandidate> unique = new LinkedHashMap<>();
        for (ErpRow row : rows()) {
            if (unique.size() < ExactProductQuery.MAX_RESULTS && matches(row, query)) {
                unique.putIfAbsent(row.candidate().sku().value(), row.candidate());
            }
        }
        return List.copyOf(unique.values());
    }

    @Override
    public AvailabilityBucket bucket(Sku sku) {
        return rows().stream()
                .filter(row -> row.candidate().sku().equals(sku))
                .findFirst()
                .map(row -> toBucket(row.quantityOnHand()))
                .orElse(AvailabilityBucket.CAN_KIEM_TRA);
    }

    static AvailabilityBucket toBucket(Integer quantityOnHand) {
        if (quantityOnHand == null || quantityOnHand < 0) {
            return AvailabilityBucket.CAN_KIEM_TRA;
        }
        if (quantityOnHand == 0) {
            return AvailabilityBucket.HET_HANG;
        }
        return quantityOnHand >= LOW_STOCK_THRESHOLD ? AvailabilityBucket.CON_HANG : AvailabilityBucket.CAN_KIEM_TRA;
    }

    private List<ErpRow> rows() {
        List<String> raw;
        try {
            raw = backend.fetchProductRows();
        } catch (RuntimeException e) {
            log.warn("erp.catalog.unavailable code=SERVICE_UNAVAILABLE exception={}", e.getClass().getSimpleName());
            throw new CatalogUnavailableException();
        }
        List<ErpRow> rows = new ArrayList<>();
        int skipped = 0;
        for (String json : raw) {
            Optional<ErpRow> row = parse(json);
            if (row.isPresent()) {
                rows.add(row.get());
            } else {
                skipped++;
            }
        }
        if (skipped > 0) {
            log.warn("erp.catalog.rows_skipped count={}", skipped);
        }
        return rows;
    }

    /** Reads only allowlisted keys; any shape problem skips the whole row. */
    private Optional<ErpRow> parse(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            if (node == null || !node.isObject()) {
                return Optional.empty();
            }
            ProductCandidate candidate = new ProductCandidate(
                    new Sku(string(node, "sku")),
                    node.hasNonNull("oe") ? string(node, "oe") : null,
                    string(node, "category"),
                    SOURCE_PROVENANCE,
                    string(node, "sourceRef"));
            JsonNode quantity = node.get("quantityOnHand");
            Integer quantityOnHand = quantity != null && quantity.isInt() ? quantity.intValue() : null;
            String vehicleModel = node.hasNonNull("vehicleModel") ? string(node, "vehicleModel") : null;
            return Optional.of(new ErpRow(candidate, vehicleModel, quantityOnHand));
        } catch (JacksonException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private static String string(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException("missing or non-string field");
        }
        return value.stringValue();
    }

    private static boolean matches(ErpRow row, ExactProductQuery query) {
        return switch (query) {
            case ExactProductQuery.ByOe byOe -> byOe.oe().equals(row.candidate().oe());
            case ExactProductQuery.ByCategoryAndModel byCategory ->
                    normalize(byCategory.category()).equals(normalize(row.candidate().category()))
                            && row.vehicleModel() != null
                            && normalize(byCategory.vehicleModel()).equals(normalize(row.vehicleModel()));
        };
    }

    private static String normalize(String text) {
        return text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** Parsed row after stripping. Quantity is INTERNAL and only used for the bucket mapping. */
    private record ErpRow(ProductCandidate candidate, String vehicleModel, Integer quantityOnHand) {
    }
}
