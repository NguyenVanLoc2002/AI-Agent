package com.secureerp.agent.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Structured technical context (AI_GATEWAY §1; fields allowed by BRD §08).
 *
 * <p>{@code maskedVin} holds only the WMI followed by 14 asterisks (A-11, APPROVED 2026-11-18), so
 * a full VIN cannot be placed here. The full VIN stays server-side behind {@code vinRef}.
 */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record TechnicalContext(
        String maskedVin,
        String vinRef,
        String vehicleModel,
        String partCategory,
        Integer requestedQuantity,
        List<ProductRef> verifiedProducts,
        String quoteHandle) {

    public static final int MAX_PRODUCTS = 10;
    public static final int MAX_QUANTITY = 9999;

    static final Pattern MASKED_VIN = Pattern.compile("^[A-HJ-NPR-Z0-9]{3}\\*{14}$");
    public static final String VIN_REF_PREFIX = "vin_";
    public static final String QUOTE_HANDLE_PREFIX = "qh_";

    public TechnicalContext {
        EgressText.optionalMatching(maskedVin, "maskedVin", MASKED_VIN);
        EgressText.optionalServerToken(vinRef, "vinRef", VIN_REF_PREFIX);
        EgressText.optionalText(vehicleModel, "vehicleModel", 80);
        if (partCategory != null) {
            EgressText.categoryLabel(partCategory, "partCategory");
        }
        if (requestedQuantity != null && (requestedQuantity < 1 || requestedQuantity > MAX_QUANTITY)) {
            throw EgressText.invalid("requestedQuantity");
        }
        verifiedProducts = EgressText.list(verifiedProducts, "verifiedProducts", MAX_PRODUCTS);
        EgressText.optionalServerToken(quoteHandle, "quoteHandle", QUOTE_HANDLE_PREFIX);
    }
}
