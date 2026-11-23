package com.secureerp.agent.policy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SEC03-F3 (A-58): screens what a model answered (its text and the names and arguments of its tool proposals) for
 * honeytokens before anything else reads it. A model answer is untrusted (AI_GATEWAY §4); a honeytoken in it
 * means a restricted value reached the model or was made up to look like one, so the answer must not be stored,
 * shown or acted on. Only honeytokens are screened here: money, phone and link rules stay in the agent's
 * output validation, where the turn's allowed part codes are known.
 */
@FunctionalInterface
public interface ResponseScreen {

    /**
     * A honeytoken found in the answer (A-59: recorded with its structural path, never its value).
     *
     * @param honeytokenId the registered ID
     * @param valueIndex   the index in the screened values of the one value that holds it, or {@code -1} when it is
     *                     assembled across values
     */
    record Hit(String honeytokenId, int valueIndex) {

        public Hit {
            Objects.requireNonNull(honeytokenId, "honeytokenId");
            if (valueIndex < -1) {
                throw new IllegalArgumentException("valueIndex");
            }
        }
    }

    /**
     * @param values the answer text and each tool proposal's name and arguments, in response order; null values
     *               are skipped (their index still counts)
     * @return the first honeytoken found in one value, in one decoded layer of it, or assembled across values
     */
    Optional<Hit> honeytokenIn(List<String> values);
}
