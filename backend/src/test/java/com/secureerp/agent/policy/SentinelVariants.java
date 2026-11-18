package com.secureerp.agent.policy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * T-SEC01-VARIANTS generator: ≥20 encodings of a digit sentinel (AI_GATEWAY §2.3). Keys are variant
 * IDs. Test names use the IDs only, so sentinel values never appear in build logs.
 */
public final class SentinelVariants {

    /** Synthetic sentinels from SECURITY_TEST_MATRIX SEC-01 / DATA_CLASSIFICATION §4. */
    public static final String COST = "987654321";
    public static final String CREDIT = "123456789";
    public static final String PRICE_A = "555444333";

    private static final String[] VI = {"không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"};
    private static final String[] EN = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};

    private SentinelVariants() {
    }

    public static Map<String, String> of(String digits) {
        Map<String, String> v = new LinkedHashMap<>();
        String g = digits.substring(0, 3) + "%s" + digits.substring(3, 6) + "%s" + digits.substring(6);
        v.put("V01-plain", digits);
        v.put("V02-dots", g.formatted(".", "."));
        v.put("V03-commas", g.formatted(",", ","));
        v.put("V04-spaces", g.formatted(" ", " "));
        v.put("V05-underscores", g.formatted("_", "_"));
        v.put("V06-nbsp", g.formatted(" ", " "));
        v.put("V07-zero-width", interleave(digits, "​"));
        v.put("V08-fullwidth", mapDigits(digits, '０'));
        v.put("V09-arabic-indic", mapDigits(digits, '٠'));
        v.put("V10-dong-sign", g.formatted(".", ".") + "₫");
        v.put("V11-vnd-suffix", digits + " VND");
        v.put("V12-base64", Base64.getEncoder().encodeToString(digits.getBytes(StandardCharsets.UTF_8)));
        v.put("V13-base64url-sentence", Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("gia von " + digits).getBytes(StandardCharsets.UTF_8)));
        v.put("V14-hex", HexFormat.of().formatHex(digits.getBytes(StandardCharsets.US_ASCII)));
        v.put("V15-escaped-hex", digits.chars().mapToObj(c -> "\\x" + Integer.toHexString(c)).collect(Collectors.joining()));
        v.put("V16-percent", digits.chars().mapToObj(c -> "%" + Integer.toHexString(c)).collect(Collectors.joining()));
        v.put("V17-unicode-escape", digits.chars().mapToObj(c -> String.format("\\u%04x", c)).collect(Collectors.joining()));
        v.put("V18-html-decimal", digits.chars().mapToObj(c -> "&#" + c + ";").collect(Collectors.joining()));
        v.put("V19-html-hex", digits.chars().mapToObj(c -> "&#x" + Integer.toHexString(c) + ";").collect(Collectors.joining()));
        v.put("V20-scientific", digits.charAt(0) + "." + digits.substring(1) + "e" + (digits.length() - 1));
        v.put("V21-scientific-comma", digits.charAt(0) + "," + digits.substring(1) + "E+" + (digits.length() - 1));
        v.put("V22-spelled-vi", spelled(digits, VI));
        v.put("V23-spelled-en", spelled(digits, EN));
        v.put("V24-hyphens", interleave(digits, "-"));
        v.put("V25-in-sentence", "gia von la " + digits + " dong nhe");
        v.put("V26-apostrophes", g.formatted("'", "'"));
        // Second review M1: one junk byte appended, and a letter glued in front of the base64 run.
        byte[] withJunk = (digits + (char) 1).getBytes(StandardCharsets.UTF_8);
        v.put("V27-base64-junk-byte", Base64.getEncoder().encodeToString(withJunk));
        v.put("V28-hex-junk-byte", HexFormat.of().formatHex(digits.getBytes(StandardCharsets.US_ASCII)) + "ff");
        v.put("V29-base64-prefixed", "x" + Base64.getEncoder().encodeToString(digits.getBytes(StandardCharsets.UTF_8)));
        return v;
    }

    private static String interleave(String digits, String separator) {
        return digits.chars().mapToObj(c -> String.valueOf((char) c)).collect(Collectors.joining(separator));
    }

    private static String mapDigits(String digits, char zero) {
        StringBuilder b = new StringBuilder();
        digits.chars().forEach(c -> b.append((char) (zero + (c - '0'))));
        return b.toString();
    }

    private static String spelled(String digits, String[] words) {
        return digits.chars().mapToObj(c -> words[c - '0']).collect(Collectors.joining(" "));
    }
}
