package com.secureerp.agent.gateway;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Independent oracle for captured bytes, audit rows and logs (security review M5). It deliberately
 * does NOT reuse the policy's detector, so a blind spot in the policy cannot hide itself:
 * <ul>
 *   <li>plain, case-insensitive substrings of every registered sentinel;</li>
 *   <li>digit sentinels after NFKC and removal of common separators between digits;</li>
 *   <li>{@link #containsExact} for the exact injected variant string and its JSON-escaped form.</li>
 * </ul>
 * It has no all-digits projection, so timestamps or PIDs in logs cannot cause spurious failures
 * (review L6).
 */
public final class EgressScan {

    /** Plain forms of every registered synthetic sentinel (DATA_CLASSIFICATION §4). */
    public static final List<String> PLAIN_SENTINELS = List.of(
            "987654321", "123456789", "555444333", "666777888", "0399000111", "ZZHoney",
            "honey.customer.a@example.invalid", "APASHT_KEY_");

    private static final List<String> DIGIT_SENTINELS =
            List.of("987654321", "123456789", "555444333", "666777888", "0399000111");

    private static final Pattern DIGIT_SEPARATORS = Pattern.compile("(?<=\\d)[\\s.,_'  ]+(?=\\d)");

    private EgressScan() {
    }

    public static boolean containsSentinel(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (PLAIN_SENTINELS.stream().anyMatch(s -> lower.contains(s.toLowerCase(Locale.ROOT)))) {
            return true;
        }
        String collapsed = DIGIT_SEPARATORS.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC)).replaceAll("");
        return DIGIT_SENTINELS.stream().anyMatch(collapsed::contains);
    }

    public static boolean containsSentinel(byte[] body) {
        return containsSentinel(new String(body, StandardCharsets.UTF_8));
    }

    public static long countWithSentinel(Stream<byte[]> bodies) {
        return bodies.filter(EgressScan::containsSentinel).count();
    }

    /** True if the exact injected string, raw or JSON-escaped, occurs in the captured body. */
    public static boolean containsExact(byte[] body, String injected) {
        String text = new String(body, StandardCharsets.UTF_8);
        String escaped = injected.replace("\\", "\\\\").replace("\"", "\\\"");
        return text.contains(injected) || text.contains(escaped);
    }
}
