package com.secureerp.agent.catalog.internal;

import com.secureerp.agent.catalog.VinIntake;
import com.secureerp.agent.catalog.spi.Vin;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds VIN-like values in a customer message and validates them (BR-04). DESIGN PROPOSAL (EP-05):
 * <ul>
 *   <li>a standalone run of 15–19 letters and digits that contains both is VIN-like, so a typo in a
 *       VIN (wrong length, I/O/Q, check digit) is reported as {@link VinIntake.Invalid} and the
 *       customer is asked again, instead of the value being ignored (false positives: A-32);</li>
 *   <li>a VIN written in groups is found with the same rule as the inbound sanitizer
 *       ({@code policy} {@code SensitiveDataDetector.splitVin}: upper-case groups of 2+ VIN
 *       characters separated by one or two spaces, dots or dashes; consecutive groups adding up to
 *       17 with a letter and a digit), plus dash-separated groups in any letter case. Every VIN the
 *       sanitizer masks is therefore also seen here, so the model never sees a masked VIN while an
 *       older VIN stays current (EP-05 reviews: security M-1, architecture M3);</li>
 *   <li>more than {@link #MAX_CANDIDATES} distinct VIN-like values (or {@link #MAX_MATCHES} matches)
 *       is {@link VinIntake.Invalid}; the same VIN repeated is one value.</li>
 * </ul>
 * The extractor returns the validated value only; it never logs.
 */
final class VinExtractor {

    static final int MAX_CANDIDATES = 5;

    private static final Pattern RUN = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z0-9]{15,19}(?![A-Za-z0-9])");
    private static final Pattern DASHED =
            Pattern.compile("(?<![A-Za-z0-9-])[A-Za-z0-9]+(?:-[A-Za-z0-9]+){1,4}(?![A-Za-z0-9-])");
    /** Same as the sanitizer's {@code VIN_GROUPS} / {@code VIN_GROUP} (keep in sync; parity test in VinSearchIT). */
    private static final Pattern GROUPS = Pattern.compile(
            "(?<![A-Za-z0-9_])[A-HJ-NPR-Z0-9]{2,}(?:[ .\\-]{1,2}[A-HJ-NPR-Z0-9]{2,})+(?![A-Za-z0-9_])");
    private static final Pattern GROUP = Pattern.compile("[A-HJ-NPR-Z0-9]{2,}");
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");

    /** Upper bound on VIN-like matches scanned per message (work bound, not a business rule). */
    static final int MAX_MATCHES = 50;

    /** The outcome, and the VIN to store when it is {@link VinIntake.Accepted}. */
    record Extraction(VinIntake intake, Optional<Vin> vin) {
    }

    private VinExtractor() {
    }

    static Extraction extract(String text) {
        if (text == null || text.isEmpty()) {
            return new Extraction(new VinIntake.None(), Optional.empty());
        }
        Set<String> candidates = new LinkedHashSet<>();
        int seen = collectRuns(text, candidates) + collectDashed(text, candidates) + collectGroups(text, candidates);
        if (seen == 0) {
            return new Extraction(new VinIntake.None(), Optional.empty());
        }
        if (seen > MAX_MATCHES || candidates.size() > MAX_CANDIDATES) {
            return new Extraction(new VinIntake.Invalid(), Optional.empty());
        }
        Set<Vin> valid = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (!Vin.isValid(candidate)) {
                return new Extraction(new VinIntake.Invalid(), Optional.empty());
            }
            valid.add(new Vin(candidate));
        }
        if (valid.size() > 1) {
            return new Extraction(new VinIntake.Multiple(), Optional.empty());
        }
        Vin vin = valid.iterator().next();
        return new Extraction(new VinIntake.Accepted(vin.masked()), Optional.of(vin));
    }

    private static int collectRuns(String text, Set<String> candidates) {
        int found = 0;
        Matcher run = RUN.matcher(text);
        while (run.find()) {
            found += add(run.group(), candidates);
        }
        return found;
    }

    private static int collectDashed(String text, Set<String> candidates) {
        int found = 0;
        Matcher dashed = DASHED.matcher(text);
        while (dashed.find()) {
            String compact = dashed.group().replace("-", "");
            if (compact.length() == Vin.LENGTH) {
                found += add(compact, candidates);
            }
        }
        return found;
    }

    /** Every run of consecutive groups that adds up to 17 characters (the sanitizer stops at the first). */
    private static int collectGroups(String text, Set<String> candidates) {
        int found = 0;
        Matcher groups = GROUPS.matcher(text);
        while (groups.find()) {
            List<String> parts = new ArrayList<>();
            Matcher group = GROUP.matcher(text).region(groups.start(), groups.end());
            while (group.find()) {
                parts.add(group.group());
            }
            for (int first = 0; first < parts.size(); first++) {
                StringBuilder joined = new StringBuilder();
                for (int last = first; last < parts.size() && joined.length() < Vin.LENGTH; last++) {
                    joined.append(parts.get(last));
                    if (joined.length() == Vin.LENGTH && last > first) {
                        found += add(joined.toString(), candidates);
                    }
                }
            }
        }
        return found;
    }

    /** Adds a VIN-like value (upper case); returns 1 if it was VIN-like. The set stops growing past the limit. */
    private static int add(String compact, Set<String> candidates) {
        if (!LETTER.matcher(compact).find() || !DIGIT.matcher(compact).find()) {
            return 0;
        }
        if (candidates.size() <= MAX_CANDIDATES) {
            candidates.add(compact.toUpperCase(Locale.ROOT));
        }
        return 1;
    }
}
