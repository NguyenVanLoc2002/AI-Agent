package com.secureerp.agent.erp.internal;

import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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

    private static final List<String> PRODUCTS = List.of(
            ROW.formatted("SKU-BRK-PAD-A", "04465-0K290", "Ma phanh truoc", "Vios 2019", "sku-brk-pad-a", 12),
            ROW.formatted("SKU-BRK-PAD-B", "04465-0D150", "Ma phanh truoc", "Vios 2019", "sku-brk-pad-b", 2),
            ROW.formatted("SKU-OIL-FLT-A", "90915-YZZD4", "Loc dau", "Vios 2019", "sku-oil-flt-a", 0),
            ROW.formatted("SKU-AIR-FLT-A", "17801-0Y040", "Loc gio", "Innova 2020", "sku-air-flt-a", 40));

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

    private final MockErpFaultMode faultMode;

    MockErpBackend(MockErpFaultMode faultMode) {
        this.faultMode = Objects.requireNonNull(faultMode, "faultMode");
    }

    /** The raw price row for one customer and SKU, if the synthetic price list has one. */
    Optional<String> fetchPriceRow(String customerKey, String sku) {
        failIfConfigured();
        if (faultMode == MockErpFaultMode.MALFORMED_ROWS) {
            return Optional.of(MALFORMED_PRICE);
        }
        String price = PRICES.getOrDefault(customerKey, Map.of()).get(sku);
        return price == null ? Optional.empty()
                : Optional.of(PRICE.formatted(customerKey, sku, price));
    }

    /** The raw credit row for one customer, if the synthetic ledger knows it. */
    Optional<String> fetchCreditRow(String customerKey) {
        failIfConfigured();
        String status = CREDIT_STATUS.get(customerKey);
        return status == null ? Optional.empty() : Optional.of(CREDIT.formatted(customerKey, status));
    }

    /** The raw vehicle row for an exact VIN, if the synthetic fitment table knows it. */
    Optional<String> fetchVehicleRow(String vin) {
        failIfConfigured();
        if (faultMode == MockErpFaultMode.MALFORMED_ROWS) {
            return Optional.of(MALFORMED_VEHICLE);
        }
        return Optional.ofNullable(VEHICLES.get(vin));
    }

    /** All product rows. A real backend would filter server-side; the adapter filters here. */
    List<String> fetchProductRows() {
        failIfConfigured();
        List<String> rows = new ArrayList<>(PRODUCTS);
        if (faultMode == MockErpFaultMode.DUPLICATE_RESPONSE) {
            rows.addAll(PRODUCTS);
        }
        if (faultMode == MockErpFaultMode.MALFORMED_ROWS) {
            rows.addAll(MALFORMED);
        }
        return rows;
    }

    private void failIfConfigured() {
        switch (faultMode) {
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
