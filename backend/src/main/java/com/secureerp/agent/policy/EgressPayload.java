package com.secureerp.agent.policy;

import java.util.List;
import java.util.Objects;

/**
 * The exact bytes about to leave (as UTF-8 text) plus every JSON string value with its path, so the
 * policy can report where a hit was without reporting what it was.
 *
 * @param rawText  the final serialized payload
 * @param segments every string value, in document order
 */
public record EgressPayload(String rawText, List<Segment> segments) {

    public EgressPayload {
        Objects.requireNonNull(rawText, "rawText");
        segments = List.copyOf(segments);
    }

    /** @param fieldPath structural path such as {@code $.messages[0].text}; never a value */
    public record Segment(String fieldPath, String value) {

        public Segment {
            Objects.requireNonNull(fieldPath, "fieldPath");
            Objects.requireNonNull(value, "value");
        }
    }
}
