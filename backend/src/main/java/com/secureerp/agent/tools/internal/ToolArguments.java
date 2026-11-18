package com.secureerp.agent.tools.internal;

import java.util.List;

/** Typed, validated arguments of one tool call. Built only by {@link ArgumentParser}. */
sealed interface ToolArguments {

    /** {@code {"vinRef":"current"}}: the VIN already validated server-side for this conversation. */
    record SearchByVinRef() implements ToolArguments {
    }

    record SearchByOe(String oe) implements ToolArguments {
    }

    record SearchByCategoryAndModel(String category, String vehicleModel) implements ToolArguments {
    }

    record QuoteLines(List<Line> lines) implements ToolArguments {

        record Line(String productRef, int quantity) {
        }

        public QuoteLines {
            lines = List.copyOf(lines);
        }
    }

    record HandoffRequest(Reason reason) implements ToolArguments {

        enum Reason {
            CUSTOMER_REQUEST,
            UNSURE,
            OUT_OF_SCOPE
        }
    }
}
