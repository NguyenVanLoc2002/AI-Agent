package com.secureerp.agent.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;
import java.util.List;

/** Result of a verified product search: at most five candidates (ERP_CONNECTOR §3.2). */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record ProductSearchResult(List<ProductRef> candidates) implements ToolResultDto {

    public static final int MAX_CANDIDATES = 5;

    public ProductSearchResult {
        candidates = EgressText.list(candidates, "candidates", MAX_CANDIDATES);
    }
}
