package com.secureerp.agent.policy.internal;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonicalisation and numeric projections used before matching (AI_GATEWAY §2.3): NFKC, removal
 * of zero-width/format characters, Unicode digits to ASCII, NBSP and other spaces to a plain space,
 * digit-separator collapsing, scientific notation and spelled-out digits (Vietnamese and English), and a
 * look-alike skeleton for text-honeytoken matching (SEC03-F1, A-55).
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

    /**
     * SEC03-F1 (A-55): letters of other scripts drawn like Latin letters, keyed by code point with case kept (Greek
     * capital eta looks like "H", small eta like "n"). A code point is looked up as written, then in lower case, then
     * in upper case, so a capital whose small form is listed (and the reverse) folds too (security review H1). A
     * curated table, not the full UTS #39 data (owner 1A): look-alikes outside it are a recorded residual.
     */
    private static final Map<Integer, Character> LOOK_ALIKES = lookAlikes(
            // Cyrillic capitals, then small letters
            "АAВBСCЕEНHІIЈJКKМMОOРPЅSТTХXУYҮYԌGԚQԜWӀl"
                    + "аaсcеeіiјjоoрpѕsхxуyһhԁdԛqԝwӏlкkвbтtмmнhгrпn"
                    // Greek capitals, then small letters; the micro sign is listed itself (its capital is Greek "Μ")
                    + "ΑAΒBΕEΖZΗHΙIΚKΜMΝNΟOΡPΤTΥYΧX"
                    + "αaοoρpιiκkνvτtυuχxεeωwϲcϳjηnγyμuµu"
                    // Armenian, Cherokee capitals
                    + "օoոnսuհhԁd" + "ᏃZᎻHᎪAᏴBᏟCᎬEᏀGᎫJᏦKᏞLᎷMᏢPᏚSᎢTᏙVᏔWᏃZ"
                    // Latin letters without a decomposition, small capitals, Latin alpha
                    + "ıiøoØOłlŁLħhĦHŧtŦTđdĐDɑaʜhᴏoᴢzᴄcᴀaᴇeᴛtᴜuʏyɴnʀr");
    /**
     * Digits and signs written instead of letters, applied after lower-casing; "i" and "l" share one form because
     * "1", "l", "I", "|" and "!" are drawn alike.
     */
    private static final Map<Integer, Character> LETTER_STAND_INS = lookAlikes("0o1l2z3e4a5s7til$s@a|l!l+t€e");

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

    /**
     * SEC03-F1 (A-55): the form text honeytokens are compared in. Canonical form, accents removed, look-alike letters
     * and letter stand-ins folded to one Latin letter, lower case, and every character that is not a letter or digit
     * removed (spaces, dots, dashes, underscores, punctuation). Applied to the honeytoken and to the scanned text
     * alike, so "ZZHоney CustomerA" (Cyrillic о), "ZZH0ney CustomerA" and "ZZHoney.Customer-A" all match. Lossy and
     * for matching only.
     */
    public static String skeleton(String text) {
        String unaccented = COMBINING_MARKS.matcher(
                Normalizer.normalize(canonical(foldLookAlikes(text)), Normalizer.Form.NFD)).replaceAll("");
        StringBuilder out = new StringBuilder(unaccented.length());
        unaccented.codePoints().forEach(cp -> {
            if (isInvisibleLetter(cp)) {
                return;
            }
            int folded = Character.toLowerCase(lookAlike(cp));
            Character standIn = LETTER_STAND_INS.get(folded);
            if (standIn != null) {
                out.append(standIn.charValue());
            } else if (Character.isLetterOrDigit(folded)) {
                out.appendCodePoint(folded);
            }
        });
        return out.toString();
    }

    /**
     * Look-alikes folded before NFKC as well, because NFKC rewrites some of them into letters that look different
     * (lunate sigma "Ϲ" becomes "Σ"); the second pass catches look-alikes NFKC produces (fullwidth forms).
     */
    private static String foldLookAlikes(String text) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> out.appendCodePoint(lookAlike(cp)));
        return out.toString();
    }

    private static int lookAlike(int cp) {
        for (int candidate : new int[] {cp, Character.toLowerCase(cp), Character.toUpperCase(cp)}) {
            Character latin = LOOK_ALIKES.get(candidate);
            if (latin != null) {
                return latin;
            }
        }
        return cp;
    }

    /**
     * Letters that draw as blank or as punctuation, used as separators (security review H2): modifier letters
     * (apostrophe- and underscore-like signs), the Hangul fillers and the sinological dot.
     */
    private static boolean isInvisibleLetter(int cp) {
        return Character.getType(cp) == Character.MODIFIER_LETTER
                || cp == 0x115F || cp == 0x1160 || cp == 0x3164 || cp == 0xFFA0 || cp == 0xA78F;
    }

    private static Map<Integer, Character> lookAlikes(String pairs) {
        Map<Integer, Character> table = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length(); i += 2) {
            table.put((int) pairs.charAt(i), pairs.charAt(i + 1));
        }
        return Map.copyOf(table);
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
