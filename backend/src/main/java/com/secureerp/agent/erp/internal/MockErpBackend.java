package com.secureerp.agent.erp.internal;

import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simulated remote ERP returning raw JSON rows, as an ERP API would. Synthetic data only. Rows carry
 * fields the application must never pass on: purchase cost, margin, per-customer prices, credit
 * balance, stock quantity, warehouse and a contact name. They use the SEC-01 sentinels
 * (DATA_CLASSIFICATION §4), so the strip at the adapter boundary can be proven (T-ERP-02).
 *
 * <p>Not a real ERP API: no vendor documentation exists (D-01..D-03, ERP_CONNECTOR header).
 */
class MockErpBackend {

    private static final String ROW = "{\"sku\":\"%s\",\"oe\":\"%s\",\"category\":\"%s\",\"vehicleModel\":\"%s\","
            + "\"provenance\":\"VERIFIED\",\"sourceRef\":\"mock-erp:catalog:%s\",\"purchaseCost\":987654321,"
            + "\"margin\":\"0.42\",\"customerPriceA\":555444333,\"customerPriceB\":666777888,"
            + "\"creditBalance\":123456789,\"quantityOnHand\":%d,\"warehouse\":\"KHO-SYNTH-01\","
            + "\"supplierContact\":\"ZZHoney CustomerA\"}";

    private record Product(String sku, String oe, String category, String vehicleModel, String source, int quantity) {
    }

    private static final List<Product> PRODUCTS = List.of(
            new Product("SKU-BRK-PAD-A", "04465-0K290", "Ma phanh truoc", "Vios 2019", "sku-brk-pad-a", 12),
            new Product("SKU-BRK-PAD-B", "04465-0D150", "Ma phanh truoc", "Vios 2019", "sku-brk-pad-b", 2),
            new Product("SKU-OIL-FLT-A", "90915-YZZD4", "Loc dau", "Vios 2019", "sku-oil-flt-a", 0),
            new Product("SKU-AIR-FLT-A", "17801-0Y040", "Loc gio", "Innova 2020", "sku-air-flt-a", 40));

    private static final List<String> MALFORMED = List.of(
            "{\"sku\":987654321,\"oe\":\"04465-0K290\",\"category\":\"Ma phanh truoc\"}",
            "not json; creditBalance=123456789",
            "[\"04465-0K290\"]",
            "{\"sku\":\"SKU-BAD-ROW\",\"oe\":\"04465-0K290\",\"category\":\"Ma phanh truoc\","
                    + "\"provenance\":\"VERIFIED\"}");

    /**
     * Synthetic VIN decode / fitment rows (EP-05). Not real vehicles: WMI {@code 5ZZ}/{@code 1ZZ}/
     * {@code RLZ} are not assigned to a real manufacturer here. Rows carry an owner name, phone and
     * credit balance sentinel the adapter must drop.
     */
    static final String NA_VIN = "5ZZAPAS11RA000001";
    static final String ASIA_VIN = "RLZAPAS20RA000002";
    private static final String VEHICLE = "{\"vin\":\"%s\",\"vehicleModel\":\"%s\",\"registeredOwner\":\"ZZHoney CustomerA\","
            + "\"ownerPhone\":\"0399000111\",\"creditBalance\":123456789}";
    private static final Map<String, String> VEHICLES = Map.of(
            NA_VIN, VEHICLE.formatted(NA_VIN, "Vios 2019"),
            ASIA_VIN, VEHICLE.formatted(ASIA_VIN, "Innova 2020"));
    private static final String MALFORMED_VEHICLE = "{\"vehicleModel\":987654321,\"registeredOwner\":\"ZZHoney CustomerA\"}";

    /**
     * Synthetic ERP customer keys (EP-06). Deliberately unlike the session reference format (A-19):
     * identity maps references to these keys. A is in good standing, B is blocked for credit, C has
     * no credit record (review required) and no prices.
     */
    static final String CUSTOMER_A = "SYNTH-CUST-A";
    static final String CUSTOMER_B = "SYNTH-CUST-B";
    static final String CUSTOMER_C = "SYNTH-CUST-C";

    /**
     * Customer prices are the SEC-01 price sentinels, so a test can prove they never reach the model
     * (only the server-rendered quote card). BRK-PAD-B has a half unit to exercise rounding; AIR-FLT-A
     * has 19 significant digits, which a double cannot hold (exact parsing). Rows also
     * carry cost, margin and a contact the adapter must drop.
     */
    private static final String PRICE = "{\"customer\":\"%s\",\"sku\":\"%s\",\"unitPrice\":%s,\"currency\":\"VND\","
            + "\"priceList\":\"mock-erp:pl:contract\",\"revision\":\"r1\",\"purchaseCost\":987654321,\"margin\":\"0.42\","
            + "\"supplierContact\":\"ZZHoney CustomerA\"}";
    private static final Map<String, Map<String, String>> PRICES = Map.of(
            CUSTOMER_A, Map.of("SKU-BRK-PAD-A", "555444333", "SKU-BRK-PAD-B", "555444333.5",
                    "SKU-OIL-FLT-A", "555444333", "SKU-AIR-FLT-A", "555444333123456.7891"),
            CUSTOMER_B, Map.of("SKU-BRK-PAD-A", "666777888", "SKU-BRK-PAD-B", "666777888",
                    "SKU-OIL-FLT-A", "666777888", "SKU-AIR-FLT-A", "666777888"));
    private static final String MALFORMED_PRICE = "{\"unitPrice\":\"cheap\",\"currency\":\"VND\",\"creditBalance\":123456789}";

    private static final String CREDIT = "{\"customer\":\"%s\",\"creditStatus\":\"%s\",\"creditBalance\":123456789,"
            + "\"creditLimit\":555444333,\"contact\":\"ZZHoney CustomerA\"}";
    private static final Map<String, String> CREDIT_STATUS = Map.of(CUSTOMER_A, "OK", CUSTOMER_B, "BLOCKED");

    /**
     * Draft order answers (EP-07). Like every other row they carry fields the adapter must drop: the
     * customer key, a credit balance, a cost and an approver name.
     */
    private static final String DRAFT = "{\"draftNo\":\"%s\",\"status\":\"DRAFT\",\"externalRef\":\"%s\","
            + "\"customer\":\"%s\",\"creditBalance\":123456789,\"purchaseCost\":987654321,"
            + "\"approver\":\"ZZHoney CustomerA\"}";
    private static final String ORDER_ERROR = "{\"error\":{\"code\":\"%s\",\"message\":\"order refused: credit "
            + "balance 123456789 over limit 555444333 for ZZHoney CustomerA\"}}";
    private static final String MALFORMED_DRAFT = "{\"draftNo\":987654321,\"creditBalance\":123456789}";

    /** A draft order the simulated ERP has written. */
    record Draft(String draftNo, String customerKey, String externalRef, String idempotencyKey, int lineCount) {
    }

    private final MockErpFaultMode configuredFaultMode;
    /** Mutable so a test can change the simulated ERP between a quote and its confirmation (EP-07). */
    private final AtomicReference<MockErpFaultMode> faultMode;
    private final AtomicReference<MockErpOrderFault> orderFault = new AtomicReference<>(MockErpOrderFault.NONE);
    private final Map<String, Optional<String>> priceOverrides = new ConcurrentHashMap<>();
    private final Map<String, Integer> stockOverrides = new ConcurrentHashMap<>();
    private final Map<String, String> creditOverrides = new ConcurrentHashMap<>();
    private final List<Draft> drafts = new ArrayList<>();
    private final AtomicInteger createCalls = new AtomicInteger();
    private final AtomicInteger lookupCalls = new AtomicInteger();

    MockErpBackend(MockErpFaultMode faultMode) {
        this.configuredFaultMode = Objects.requireNonNull(faultMode, "faultMode");
        this.faultMode = new AtomicReference<>(faultMode);
    }

    /** Back to the configured fault mode, the synthetic price list, stock and ledger, and no drafts. */
    void reset() {
        faultMode.set(configuredFaultMode);
        orderFault.set(MockErpOrderFault.NONE);
        priceOverrides.clear();
        stockOverrides.clear();
        creditOverrides.clear();
        synchronized (drafts) {
            drafts.clear();
        }
        createCalls.set(0);
        lookupCalls.set(0);
    }

    void failReads(MockErpFaultMode mode) {
        faultMode.set(Objects.requireNonNull(mode, "mode"));
    }

    void failOrders(MockErpOrderFault fault) {
        orderFault.set(Objects.requireNonNull(fault, "fault"));
    }

    /** @param unitPrice the new price as JSON number text; {@code null} removes the price */
    void priceOverride(String customerKey, String sku, String unitPrice) {
        priceOverrides.put(customerKey + "|" + sku, Optional.ofNullable(unitPrice));
    }

    void stockOverride(String sku, int quantity) {
        stockOverrides.put(sku, quantity);
    }

    void creditOverride(String customerKey, String status) {
        creditOverrides.put(customerKey, status);
    }

    List<Draft> drafts() {
        synchronized (drafts) {
            return List.copyOf(drafts);
        }
    }

    int createCalls() {
        return createCalls.get();
    }

    int lookupCalls() {
        return lookupCalls.get();
    }

    /**
     * Creates a draft order and returns the raw answer. With an idempotency key the simulated ERP
     * returns the draft it already has for that key; without one every call writes a new draft, so a
     * second call would be visible as a duplicate (SEC-04).
     */
    String createDraftOrder(String customerKey, String externalRef, String idempotencyKey, int lineCount) {
        createCalls.incrementAndGet();
        MockErpOrderFault fault = orderFault.get();
        if (fault == MockErpOrderFault.TIMEOUT_BEFORE_COMMIT) {
            throw new UncheckedIOException(new SocketTimeoutException("connect timed out for " + customerKey));
        }
        if (fault == MockErpOrderFault.ERROR_WITH_SENTINEL) {
            throw new IllegalStateException(
                    "ERP failure: purchaseCost=987654321 creditBalance=123456789 contact=ZZHoney CustomerA");
        }
        if (fault == MockErpOrderFault.REJECT) {
            return ORDER_ERROR.formatted("CREDIT_HOLD");
        }
        if (fault == MockErpOrderFault.UNLISTED_ERROR) {
            return ORDER_ERROR.formatted("E-99123456789");
        }
        if (!CREDIT_STATUS.containsKey(customerKey)) {
            return ORDER_ERROR.formatted("UNKNOWN_CUSTOMER");
        }
        Draft draft;
        synchronized (drafts) {
            draft = idempotencyKey == null ? null : drafts.stream()
                    .filter(existing -> idempotencyKey.equals(existing.idempotencyKey())).findFirst().orElse(null);
            if (draft == null) {
                draft = new Draft("MOCK-DO-%06d".formatted(drafts.size() + 1), customerKey, externalRef,
                        idempotencyKey, lineCount);
                drafts.add(draft);
            }
        }
        if (fault == MockErpOrderFault.TIMEOUT_AFTER_COMMIT) {
            throw new UncheckedIOException(
                    new SocketTimeoutException("read timed out after writing order, balance 123456789"));
        }
        if (fault == MockErpOrderFault.MALFORMED_RESPONSE) {
            return MALFORMED_DRAFT;
        }
        return DRAFT.formatted(draft.draftNo(), draft.externalRef(), draft.customerKey());
    }

    /** The raw draft row for our reference, if the simulated ERP has one. */
    Optional<String> fetchDraftByExternalRef(String externalRef) {
        lookupCalls.incrementAndGet();
        failIfConfigured();
        synchronized (drafts) {
            return drafts.stream().filter(draft -> draft.externalRef().equals(externalRef)).findFirst()
                    .map(draft -> DRAFT.formatted(draft.draftNo(), draft.externalRef(), draft.customerKey()));
        }
    }

    /** The raw price row for one customer and SKU, if the synthetic price list has one. */
    Optional<String> fetchPriceRow(String customerKey, String sku) {
        failIfConfigured();
        if (faultMode.get() == MockErpFaultMode.MALFORMED_ROWS) {
            return Optional.of(MALFORMED_PRICE);
        }
        Optional<String> override = priceOverrides.get(customerKey + "|" + sku);
        String price = override != null ? override.orElse(null) : PRICES.getOrDefault(customerKey, Map.of()).get(sku);
        return price == null ? Optional.empty()
                : Optional.of(PRICE.formatted(customerKey, sku, price));
    }

    /** The raw credit row for one customer, if the synthetic ledger knows it. */
    Optional<String> fetchCreditRow(String customerKey) {
        failIfConfigured();
        String status = creditOverrides.getOrDefault(customerKey, CREDIT_STATUS.get(customerKey));
        return status == null ? Optional.empty() : Optional.of(CREDIT.formatted(customerKey, status));
    }

    /** The raw vehicle row for an exact VIN, if the synthetic fitment table knows it. */
    Optional<String> fetchVehicleRow(String vin) {
        failIfConfigured();
        if (faultMode.get() == MockErpFaultMode.MALFORMED_ROWS) {
            return Optional.of(MALFORMED_VEHICLE);
        }
        return Optional.ofNullable(VEHICLES.get(vin));
    }

    /** All product rows. A real backend would filter server-side; the adapter filters here. */
    List<String> fetchProductRows() {
        failIfConfigured();
        List<String> products = PRODUCTS.stream()
                .map(product -> ROW.formatted(product.sku(), product.oe(), product.category(), product.vehicleModel(),
                        product.source(), stockOverrides.getOrDefault(product.sku(), product.quantity())))
                .toList();
        List<String> rows = new ArrayList<>(products);
        if (faultMode.get() == MockErpFaultMode.DUPLICATE_RESPONSE) {
            rows.addAll(products);
        }
        if (faultMode.get() == MockErpFaultMode.MALFORMED_ROWS) {
            rows.addAll(MALFORMED);
        }
        return rows;
    }

    private void failIfConfigured() {
        switch (faultMode.get()) {
            case ERROR_WITH_SENTINEL -> throw new IllegalStateException(
                    "ERP failure: purchaseCost=987654321 creditBalance=123456789 contact=ZZHoney CustomerA");
            case TIMEOUT -> throw new UncheckedIOException(
                    new SocketTimeoutException("read timed out after costing 987654321"));
            default -> {
                // continue
            }
        }
    }
}
