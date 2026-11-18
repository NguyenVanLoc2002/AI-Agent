package com.secureerp.agent.policy.internal;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonicalisation and numeric projections used before matching (AI_GATEWAY §2.3): NFKC, removal
 * of zero-width/format characters, Unicode digits to ASCII, NBSP and other spaces to a plain space,
 * digit-separator collapsing, scientific notation and spelled-out digits (Vietnamese and English).
 */
public final class TextNormalizer {

    /** Separators inside a number: dot, comma, whitespace, underscore, apostrophes, middle dot. */
    private static final Pattern DIGIT_SEPARATORS = Pattern.compile("(?<=\\d)[\\s.,_'’·]+(?=\\d)");
    private static final Pattern NON_DIGITS = Pattern.compile("\\D+");
    private static final Pattern SCIENTIFIC = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*[eE]\\s*([+-]?\\d{1,3})");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WORDS = Pattern.compile("[a-z]+");

    private static final Map<String, Character> SPELLED_DIGITS = Map.ofEntries(
            Map.entry("khong", '0'), Map.entry("zero", '0'),
            Map.entry("mot", '1'), Map.entry("one", '1'),
            Map.entry("hai", '2'), Map.entry("two", '2'),
            Map.entry("ba", '3'), Map.entry("three", '3'),
            Map.entry("bon", '4'), Map.entry("tu", '4'), Map.entry("four", '4'),
            Map.entry("nam", '5'), Map.entry("lam", '5'), Map.entry("five", '5'),
            Map.entry("sau", '6'), Map.entry("six", '6'),
            Map.entry("bay", '7'), Map.entry("seven", '7'),
            Map.entry("tam", '8'), Map.entry("eight", '8'),
            Map.entry("chin", '9'), Map.entry("nine", '9'));

    private TextNormalizer() {
    }

    /** NFKC, format characters removed, digits ASCII, spaces plain. Case is preserved. */
    public static String canonical(String text) {
        String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC);
        StringBuilder out = new StringBuilder(nfkc.length());
        nfkc.codePoints().forEach(cp -> {
            int type = Character.getType(cp);
            if (type == Character.FORMAT || cp == 0x00AD || cp == 0x034F) {
                return; // zero-width space/joiners, BOM, soft hyphen, word joiner, bidi marks
            }
            if (Character.isDigit(cp)) {
                out.append((char) ('0' + Character.digit(cp, 10)));
            } else if (Character.isSpaceChar(cp) || cp == '\t' || cp == '\n' || cp == '\r') {
                out.append(cp == '\n' ? '\n' : ' ');
            } else {
                out.appendCodePoint(cp);
            }
        });
        return out.toString();
    }

    /** Lower-case canonical form used for matching. */
    public static String forMatching(String text) {
        return canonical(text).toLowerCase(Locale.ROOT);
    }

    /** "987.654.321", "987 654 321", "987_654_321" become "987654321". */
    public static String collapseDigitSeparators(String normalized) {
        return DIGIT_SEPARATORS.matcher(normalized).replaceAll("");
    }

    /** Every digit of the text concatenated (catches values split across fields or messages). */
    public static String digitsOnly(String normalized) {
        return NON_DIGITS.matcher(normalized).replaceAll("");
    }

    /** "9.87654321e8" becomes "987654321". Malformed or huge exponents are skipped. */
    public static List<String> scientificExpansions(String normalized) {
        List<String> values = new ArrayList<>();
        Matcher m = SCIENTIFIC.matcher(normalized);
        while (m.find() && values.size() < 64) {
            try {
                BigDecimal value = new BigDecimal(m.group(1).replace(',', '.'))
                        .scaleByPowerOfTen(Integer.parseInt(m.group(2)));
                if (value.abs().compareTo(BigDecimal.TEN.pow(30)) < 0) {
                    values.add(value.stripTrailingZeros().toPlainString().replace(".", ""));
                }
            } catch (NumberFormatException | ArithmeticException ignored) {
                // not a number we can expand; other projections still apply
            }
        }
        return values;
    }

    /** "chín tám bảy ..." / "nine eight seven ..." become "987...". Other words are skipped. */
    public static String spelledDigits(String normalized) {
        String ascii = COMBINING_MARKS.matcher(Normalizer.normalize(normalized, Normalizer.Form.NFD)).replaceAll("")
                .replace('đ', 'd').replace('Đ', 'd');
        StringBuilder digits = new StringBuilder();
        Matcher m = WORDS.matcher(ascii);
        while (m.find()) {
            Character digit = SPELLED_DIGITS.get(m.group());
            if (digit != null) {
                digits.append(digit);
            }
        }
        return digits.toString();
    }
}
