package com.secureerp.agent.erp.internal;

import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    private final MockErpFaultMode faultMode;

    MockErpBackend(MockErpFaultMode faultMode) {
        this.faultMode = Objects.requireNonNull(faultMode, "faultMode");
    }

    /** All product rows. A real backend would filter server-side; the adapter filters here. */
    List<String> fetchProductRows() {
        switch (faultMode) {
            case ERROR_WITH_SENTINEL -> throw new IllegalStateException(
                    "ERP failure: purchaseCost=987654321 creditBalance=123456789 contact=ZZHoney CustomerA");
            case TIMEOUT -> throw new UncheckedIOException(
                    new SocketTimeoutException("read timed out after costing 987654321"));
            default -> {
                // continue
            }
        }
        List<String> rows = new ArrayList<>(PRODUCTS);
        if (faultMode == MockErpFaultMode.DUPLICATE_RESPONSE) {
            rows.addAll(PRODUCTS);
        }
        if (faultMode == MockErpFaultMode.MALFORMED_ROWS) {
            rows.addAll(MALFORMED);
        }
        return rows;
    }
}
