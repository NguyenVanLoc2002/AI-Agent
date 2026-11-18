package com.secureerp.agent.gateway;

import com.secureerp.agent.gateway.api.AvailabilityBucket;
import com.secureerp.agent.gateway.api.HandoffResult;
import com.secureerp.agent.gateway.api.ModelRequest;
import com.secureerp.agent.gateway.api.ProductRef;
import com.secureerp.agent.gateway.api.ProductSearchResult;
import com.secureerp.agent.gateway.api.Provenance;
import com.secureerp.agent.gateway.api.QuoteRequestResult;
import com.secureerp.agent.gateway.api.ResponseBudget;
import com.secureerp.agent.gateway.api.SafeMessage;
import com.secureerp.agent.gateway.api.TechnicalContext;
import com.secureerp.agent.gateway.api.ToolResultDto;
import com.secureerp.agent.gateway.api.ToolSpec;
import java.util.List;
import java.util.UUID;

/** Synthetic, deterministic egress fixtures (no real data). */
public final class EgressFixtures {

    public static final String TRACE_ID = "0b6c3a52-4f7e-4c1d-9a8b-2e5f6d7c8b9a";
    public static final String QUOTE_HANDLE = "qh_SyntheticHandleAAAAAAA";

    private EgressFixtures() {
    }

    public static ProductRef product() {
        return new ProductRef("pr_SynthProd01AAAAAAAAAAA", "SKU-BRK-PAD-A", "04465-0K290", "Ma phanh truoc",
                Provenance.VERIFIED, AvailabilityBucket.CON_HANG);
    }

    public static TechnicalContext context() {
        return new TechnicalContext("JTD**************", "vin_SyntheticVinRef01AAAAA", "Vios 2019", "Ma phanh",
                2, List.of(product()), null);
    }

    public static List<ToolResultDto> toolResults() {
        return List.of(new ProductSearchResult(List.of(product())),
                new QuoteRequestResult(QuoteRequestResult.Status.ISSUED, QUOTE_HANDLE, 1),
                new HandoffResult(true));
    }

    public static ToolSpec searchTool() {
        return new ToolSpec("searchVerifiedProduct", "{\"type\":\"object\",\"additionalProperties\":false}");
    }

    public static ModelRequest request(String customerText) {
        return request(TRACE_ID, List.of(new SafeMessage(SafeMessage.Role.CUSTOMER, customerText)));
    }

    public static ModelRequest request(String traceId, List<SafeMessage> messages) {
        return new ModelRequest(traceId, "apas.customer-chat.v1", messages, context(), toolResults(),
                List.of(searchTool()), ResponseBudget.STANDARD);
    }

    public static ModelRequest request(List<SafeMessage> messages, TechnicalContext context,
            List<ToolResultDto> toolResults) {
        return new ModelRequest(TRACE_ID, "apas.customer-chat.v1", messages, context, toolResults,
                List.of(searchTool()), ResponseBudget.STANDARD);
    }

    /** Deterministic UUID-shaped trace ID for scripted conversations. */
    public static String traceId(long seed) {
        return new UUID(0x5EC01A0000000000L | seed, 0x8000000000000000L | seed).toString();
    }
}
