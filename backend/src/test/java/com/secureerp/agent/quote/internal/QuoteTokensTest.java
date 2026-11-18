package com.secureerp.agent.quote.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** ADR-008 handle format; a handle never looks like a VIN or a long number (EP-06 regression). */
class QuoteTokensTest {

    /** Independent copy of the T-TOOL-13 argument rule (tools ArgumentParser), as a test oracle. */
    private static final Pattern VIN = Pattern.compile(
            "(?=[A-HJ-NPR-Z0-9]{0,16}\\d)(?=[A-HJ-NPR-Z0-9]{0,16}[A-HJ-NPR-Z])[A-HJ-NPR-Z0-9]{17}");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-_/]");

    @Test
    void handlesAreUniqueWellFormedAndNeverVinLike() {
        QuoteTokens tokens = new QuoteTokens();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String handle = tokens.newHandle();
            String upper = handle.toUpperCase(Locale.ROOT);

            assertThat(handle).matches(QuoteTokens.HANDLE);
            assertThat(VIN.matcher(upper).find() || VIN.matcher(SEPARATORS.matcher(upper).replaceAll("")).find())
                    .as(handle).isFalse();
            assertThat(handle.replace("-", "").replace("_", "")).doesNotContainPattern("\\d{7}");
            assertThat(seen.add(handle)).isTrue();
        }
    }

    @Test
    void hashIsSha256HexAndPublicRefHasNoLookAlikes() {
        QuoteTokens tokens = new QuoteTokens();

        assertThat(QuoteTokens.hash("qh_AAAAAAAAAAAAAAAAAAAAAA")).matches("^[0-9a-f]{64}$");
        assertThat(tokens.newPublicRef()).matches("^BG-[A-HJ-NP-Z2-9]{8}$");
    }
}
