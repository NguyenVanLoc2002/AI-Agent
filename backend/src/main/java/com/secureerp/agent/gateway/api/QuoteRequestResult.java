package com.secureerp.agent.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;
import java.util.Objects;

/**
 * Outcome of a quote request as the model sees it (T-SEC01-QUOTE): status, opaque handle, line
 * count. NOT_QUOTED is the same for every reason, so credit or stock cannot be inferred from it.
 * A-14 (the business accepting that UX) is still PROPOSED; this type only makes it possible.
 */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record QuoteRequestResult(Status status, String quoteHandle, Integer lineCount) implements ToolResultDto {

    public static final int MAX_LINES = 20;

    public enum Status {
        ISSUED,
        NOT_QUOTED
    }

    public QuoteRequestResult {
        Objects.requireNonNull(status, "status");
        EgressText.optionalMatching(quoteHandle, "quoteHandle", TechnicalContext.QUOTE_HANDLE);
        if (lineCount != null && (lineCount < 0 || lineCount > MAX_LINES)) {
            throw EgressText.invalid("lineCount");
        }
        if ((status == Status.ISSUED) != (quoteHandle != null)) {
            throw EgressText.invalid("quoteHandle");
        }
    }
}
