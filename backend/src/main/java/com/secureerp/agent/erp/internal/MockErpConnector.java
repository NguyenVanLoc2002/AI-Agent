package com.secureerp.agent.erp.internal;

import com.secureerp.agent.catalog.spi.AvailabilityBucket;
import com.secureerp.agent.catalog.spi.AvailabilityBucketPort;
import com.secureerp.agent.catalog.spi.CatalogUnavailableException;
import com.secureerp.agent.catalog.spi.ExactProductQuery;
import com.secureerp.agent.catalog.spi.ProductCandidate;
import com.secureerp.agent.catalog.spi.Provenance;
import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.catalog.spi.Vin;
import com.secureerp.agent.catalog.spi.VerifiedProductPort;
import com.secureerp.agent.identity.AuthenticatedCustomerRef;
import com.secureerp.agent.identity.ErpCustomerKeys;
import com.secureerp.agent.platform.Money;
import com.secureerp.agent.quote.spi.AvailabilityPort;
import com.secureerp.agent.quote.spi.CreditEligibilityPort;
import com.secureerp.agent.quote.spi.EligibilityStatus;
import com.secureerp.agent.quote.spi.PriceQuote;
import com.secureerp.agent.quote.spi.PricingPort;
import com.secureerp.agent.quote.spi.QuoteSourceUnavailableException;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.OptionalInt;
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
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mock ERP connector for the PoC (target name {@code MockErpConnector}, ERP_CONNECTOR §5; ADR-005
 * and A-07 ACCEPTED/APPROVED 2026-11-18). Implements the catalog ports (SEC-01a, EP-05) and the quote
 * ports for price, stock and credit (EP-06); draft orders come with EP-07. Mock-only assumption: the
 * fake backend returns whole tables and the adapter filters (a real adapter queries per key).
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
public class MockErpConnector implements VerifiedProductPort, AvailabilityBucketPort, PricingPort, AvailabilityPort,
        CreditEligibilityPort {

    private static final Logger log = LoggerFactory.getLogger(MockErpConnector.class);

    /** Bucket threshold: DESIGN PROPOSAL (A-12); not derived from any real ERP. */
    static final int LOW_STOCK_THRESHOLD = 3;

    private static final int MAX_VEHICLE_MODEL = 80;

    /**
     * Provenance is a property of the source, assigned by the adapter from its configuration, never
     * read from row content (architecture review M7): the synthetic Mock catalog is the PoC's
     * verified source. Whether a real catalog counts as VERIFIED is OPEN (D-05).
     */
    static final Provenance SOURCE_PROVENANCE = Provenance.VERIFIED;

    /**
     * Products found through a VIN decode / fitment table are "Dữ liệu tra cứu" (BR-06), never
     * VERIFIED: the vehicle model narrows the catalog, it does not identify the exact part. DESIGN
     * PROPOSAL (EP-05); {@code catalog} caps VIN results at CATALOG_LOOKUP as well. The label belongs
     * to the query, not the SKU: an exact OE search for the same SKU still answers VERIFIED, so
     * a VIN-found part may be quoted after an exact OE re-search (A-31 APPROVED 2026-11-18).
     */
    static final Provenance FITMENT_PROVENANCE = Provenance.CATALOG_LOOKUP;

    private final MockErpBackend backend;
    /** Fractional JSON numbers become BigDecimal, never double: prices stay exact (rule 20, EP-06 review). */
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    public MockErpConnector(@Value("${apas.erp.mock.fault-mode:NONE}") MockErpFaultMode faultMode) {
        this.backend = new MockErpBackend(faultMode);
    }

    @Override
    public List<ProductCandidate> search(ExactProductQuery query) {
        if (query instanceof ExactProductQuery.ByVin byVin) {
            return searchByVin(byVin.vin());
        }
        Map<String, ProductCandidate> unique = new LinkedHashMap<>();
        for (ErpRow row : rows()) {
            if (unique.size() < ExactProductQuery.MAX_RESULTS && matches(row, query)) {
                unique.putIfAbsent(row.candidate().sku().value(), row.candidate());
            }
        }
        return List.copyOf(unique.values());
    }

    /** Vehicle model from the fitment row (only that key is read), then the products fitting it. */
    private List<ProductCandidate> searchByVin(Vin vin) {
        Optional<String> vehicleModel = vehicleModel(vin);
        if (vehicleModel.isEmpty()) {
            return List.of();
        }
        String model = normalize(vehicleModel.get());
        Map<String, ProductCandidate> unique = new LinkedHashMap<>();
        for (ErpRow row : rows()) {
            if (unique.size() < ExactProductQuery.MAX_RESULTS && row.vehicleModel() != null
                    && model.equals(normalize(row.vehicleModel()))) {
                ProductCandidate c = row.candidate();
                unique.putIfAbsent(c.sku().value(),
                        new ProductCandidate(c.sku(), c.oe(), c.category(), FITMENT_PROVENANCE, c.sourceRef()));
            }
        }
        return List.copyOf(unique.values());
    }

    private Optional<String> vehicleModel(Vin vin) {
        Optional<String> raw;
        try {
            raw = backend.fetchVehicleRow(vin.value());
        } catch (RuntimeException e) {
            log.warn("erp.vehicle.unavailable code=SERVICE_UNAVAILABLE exception={}", e.getClass().getSimpleName());
            throw new CatalogUnavailableException();
        }
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        try {
            JsonNode node = mapper.readTree(raw.get());
            String model = node != null && node.isObject() ? string(node, "vehicleModel") : null;
            if (model == null || model.isBlank() || model.length() > MAX_VEHICLE_MODEL) {
                throw new IllegalArgumentException("invalid vehicle model");
            }
            return Optional.of(model);
        } catch (JacksonException | IllegalArgumentException e) {
            // Never the VIN or the row: a malformed fitment row means "no fitment".
            log.warn("erp.vehicle.row_skipped");
            return Optional.empty();
        }
    }

    /**
     * Customer price (EP-06, ERP_CONNECTOR §3.3). Only {@code unitPrice}, {@code currency},
     * {@code priceList} and {@code revision} are read; cost, margin and contact are dropped here. A
     * malformed row means "no price" (fail closed), never a partial price.
     */
    @Override
    public Optional<PriceQuote> priceFor(AuthenticatedCustomerRef customer, Sku sku) {
        Optional<String> raw;
        try {
            raw = backend.fetchPriceRow(ErpCustomerKeys.keyOf(customer), sku.value());
        } catch (RuntimeException e) {
            log.warn("erp.price.unavailable code=SERVICE_UNAVAILABLE exception={}", e.getClass().getSimpleName());
            throw new QuoteSourceUnavailableException();
        }
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        try {
            JsonNode node = mapper.readTree(raw.get());
            JsonNode unitPrice = node == null ? null : node.get("unitPrice");
            if (unitPrice == null || !unitPrice.isNumber()) {
                throw new IllegalArgumentException("missing or non-numeric price");
            }
            BigDecimal amount = unitPrice.decimalValue();
            Money price = Money.of(amount, Currency.getInstance(string(node, "currency")));
            String revision = node.hasNonNull("revision") ? string(node, "revision") : null;
            return Optional.of(new PriceQuote(price, string(node, "priceList"), revision));
        } catch (JacksonException | IllegalArgumentException | NullPointerException e) {
            log.warn("erp.price.row_skipped");
            return Optional.empty();
        }
    }

    /** Stock quantity (INTERNAL, ERP_CONNECTOR §3.4); unknown or malformed means "not available". */
    @Override
    public OptionalInt quantityAvailable(Sku sku) {
        List<ErpRow> all;
        try {
            all = rows();
        } catch (CatalogUnavailableException e) {
            throw new QuoteSourceUnavailableException();
        }
        return all.stream()
                .filter(row -> row.candidate().sku().equals(sku) && row.quantityOnHand() != null
                        && row.quantityOnHand() >= 0)
                .mapToInt(ErpRow::quantityOnHand)
                .findFirst();
    }

    /**
     * Credit status (RESTRICTED, ERP_CONNECTOR §3.5). Only {@code creditStatus} is read; balance and
     * limit are dropped. Unknown customer or status: REVIEW_REQUIRED (fail closed).
     */
    @Override
    public EligibilityStatus check(AuthenticatedCustomerRef customer) {
        Optional<String> raw;
        try {
            raw = backend.fetchCreditRow(ErpCustomerKeys.keyOf(customer));
        } catch (RuntimeException e) {
            log.warn("erp.eligibility.unavailable code=SERVICE_UNAVAILABLE exception={}", e.getClass().getSimpleName());
            throw new QuoteSourceUnavailableException();
        }
        if (raw.isEmpty()) {
            return EligibilityStatus.REVIEW_REQUIRED;
        }
        try {
            return switch (string(mapper.readTree(raw.get()), "creditStatus")) {
                case "OK" -> EligibilityStatus.ELIGIBLE;
                case "BLOCKED" -> EligibilityStatus.NOT_ELIGIBLE;
                default -> EligibilityStatus.REVIEW_REQUIRED;
            };
        } catch (JacksonException | IllegalArgumentException | NullPointerException e) {
            return EligibilityStatus.REVIEW_REQUIRED;
        }
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
            case ExactProductQuery.ByVin byVin -> false;
        };
    }

    private static String normalize(String text) {
        return text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** Parsed row after stripping. Quantity is INTERNAL: bucket mapping and the quote stock check only. */
    private record ErpRow(ProductCandidate candidate, String vehicleModel, Integer quantityOnHand) {
    }
}
