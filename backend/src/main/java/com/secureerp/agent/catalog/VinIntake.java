package com.secureerp.agent.catalog;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Result of reading VINs from one inbound message (UC-02 AC-02.1). Carries no full VIN: only the
 * masked form (WMI + 14 asterisks) the model may see (A-11).
 *
 * <p>{@link Invalid} and {@link Multiple} mean "ask the customer again": the caller must not call the
 * model or a lookup tool for this message (AC-02.1); the conversation's stored VIN is cleared so no
 * stale vehicle is searched. {@link Unavailable} means the VIN store failed: the caller must not call
 * the model for this message and hands off (fail closed, EP-05 reviews L-2/L2).
 */
public sealed interface VinIntake {

    /** No VIN-like value in the message; the stored VIN (if any) is unchanged. */
    record None() implements VinIntake {
    }

    /** Exactly one valid VIN; it is now the conversation's current VIN. */
    record Accepted(String maskedVin) implements VinIntake {

        private static final Pattern MASKED = Pattern.compile("^[A-HJ-NPR-Z0-9]{3}\\*{14}$");

        public Accepted {
            Objects.requireNonNull(maskedVin, "maskedVin");
            if (!MASKED.matcher(maskedVin).matches()) {
                throw new IllegalArgumentException("invalid masked vin format");
            }
        }
    }

    /** A VIN-like value that fails BR-04 (length, I/O/Q, check digit). */
    record Invalid() implements VinIntake {
    }

    /** More than one distinct valid VIN in one message: the customer must say which vehicle. */
    record Multiple() implements VinIntake {
    }

    /** The VIN store failed; a clear of the previous VIN was attempted. */
    record Unavailable() implements VinIntake {
    }
}
