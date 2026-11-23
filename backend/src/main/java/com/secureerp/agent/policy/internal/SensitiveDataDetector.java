package com.secureerp.agent.policy.internal;

import com.secureerp.agent.policy.PolicyCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects honeytokens (in every variant listed in AI_GATEWAY §2.3) and sensitive patterns: money in
 * price/currency context, phone numbers, email addresses and full VINs. Returns only a code and a
 * honeytoken ID, never the match.
 */
@Component
public class SensitiveDataDetector {

    /**
     * A number followed by a currency word, e.g. "1.250.000đ", "2tr", "500000 dong". Unaccented
     * "dong co" ("động cơ", engine) is not money (security review M3).
     */
    static final Pattern MONEY_SUFFIX = Pattern.compile(
            "(?U)(?<![\\w.,])\\d(?:[\\d.,' _]*\\d)?\\s*"
                    + "(?:₫|đ|vnd|vnđ|dong(?!\\s+co(?!\\w))|đồng|k|nghìn|nghin|ngàn|ngan|tr|triệu|trieu|usd)(?!\\w)");
    /**
     * A price keyword directly followed by an amount of 4+ digits, allowing only short filler words
     * ("giá là 450000", "tổng cộng: 1.200.000", "giá vốn 987..."). A wider gap matched "xe gia dinh,
     * doi 2019" (security review M3).
     *
     * <p>A-39(h): a part code is not an amount. The exemption is narrow and checked on the whole token
     * before the amount is matched (no backtracking into a shorter amount): exactly five digits, then
     * either "-" or a letter, then a tail that has both a letter and a digit and does not start with a
     * multiplier or currency word at all ("báo giá 04465-0K290", "giá 04152YZZA1"). Everything else stays
     * money (fail-closed): "giá 450000x2", "giá 45000kx2", "giá 45000đồng2", "giá 45000-VND",
     * "giá 450000-500000", a digit-only OE such as "giá 90915-10004", and OE codes whose tail starts with
     * one of those words ("giá 48510-K0010") (security reviews 1 and 2 of A-39(h)). Residual: Cyrillic
     * look-alike letters ("х", "к") are not folded here (IMPLEMENTATION_PLAN §2a SEC03-F1-MONEY; A-55 folds them for
     * text honeytokens only).
     */
    static final Pattern MONEY_PREFIX = Pattern.compile(
            "(?U)(?:(?<!\\w)(?<!quoc )(?<!quốc )(?<!tham )(?:giá|gia|price|tổng|tong|total|thành tiền|thanh tien|vốn|von|cost|công nợ|cong no|nợ|debt|hạn mức|han muc)(?!\\w)"
                    + "(?:\\s*(?:là|la|khoảng|khoang|chỉ|chi|tầm|tam|cộng|cong|vốn|von|bán|ban|mới|moi|[:=]))*\\s*|[₫$]\\s?)"
                    + "(?!\\d{5}(?:-|(?=\\p{L}))"
                    + "(?!(?i:x|k|đ|d|vnd|vnđ|dong|đồng|nghìn|nghin|ngàn|ngan|tr|triệu|trieu|usd))"
                    + "(?=[\\p{L}\\d]*\\p{L})(?=[\\p{L}\\d]*\\d)[\\p{L}\\d]+(?![\\p{L}\\d]))"
                    + "\\d(?:[\\d.,' _]*\\d){3,}");
    /**
     * VN mobile (10 digits) or landline (11 digits); not inside a longer alphanumeric token such as a
     * UUID. Up to three separators (space, dot, dash, slash, brackets) between digits, so
     * "(090) 123 4567", "090/123/4567" and double spaces are covered (second review M2).
     */
    static final Pattern PHONE = Pattern.compile(
            "(?<![\\w+])\\(?(?:\\+?84|0)[\\s./()\\-]{0,3}[35789](?:[\\s./()\\-]{0,3}\\d){8}(?!\\w)"
                    + "|(?<![\\w+])\\(?(?:\\+?84|0)[\\s./()\\-]{0,3}2(?:[\\s./()\\-]{0,3}\\d){9}(?!\\w)");
    static final Pattern EMAIL = Pattern.compile("[a-z0-9._%+-]+@[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}",
            Pattern.CASE_INSENSITIVE);
    /**
     * 17 VIN characters (no I, O, Q) with at least one digit and one letter, not inside a longer
     * token. "_" counts as a token character, so the tail of "vin_..." or a "qh_..." handle is not
     * read as a VIN (security review M3). "-" does not: "VIN-1HGCM82633A004352" in free text is a
     * full VIN (third review M-C); opaque refs never reach this pattern (honeytoken check only).
     */
    static final Pattern FULL_VIN = Pattern.compile(
            "(?<![A-Za-z0-9_])(?=[A-HJ-NPR-Za-hj-npr-z0-9]{0,16}\\d)(?=[A-HJ-NPR-Za-hj-npr-z0-9]{0,16}[A-HJ-NPR-Za-hj-npr-z])"
                    + "[A-HJ-NPR-Za-hj-npr-z0-9]{17}(?![A-Za-z0-9_])");
    /**
     * Upper-case VIN characters in groups of 2+ separated by one or two spaces, dots or dashes
     * ("JTDBT-923X7-1234567", "JTDBT 923X7 1234567"). Any run of consecutive groups that adds up to
     * 17 characters with a digit and a letter is a full VIN (second review M3). Lower-case split
     * VINs are not covered (residual, AI_GATEWAY §6a).
     */
    private static final Pattern VIN_GROUPS = Pattern.compile(
            "(?<![A-Za-z0-9_])[A-HJ-NPR-Z0-9]{2,}(?:[ .\\-]{1,2}[A-HJ-NPR-Z0-9]{2,})+(?![A-Za-z0-9_])");
    private static final Pattern VIN_GROUP = Pattern.compile("[A-HJ-NPR-Z0-9]{2,}");
    private static final int VIN_LENGTH = 17;

    /** Start (inclusive) and end (exclusive) of a full VIN written in separated groups. */
    record Span(int start, int end) {
    }

    /** First full VIN written in separated groups, if any. */
    static Optional<Span> splitVin(String text) {
        Matcher groups = VIN_GROUPS.matcher(text);
        while (groups.find()) {
            List<int[]> parts = new ArrayList<>();
            Matcher group = VIN_GROUP.matcher(text).region(groups.start(), groups.end());
            while (group.find()) {
                parts.add(new int[] {group.start(), group.end()});
            }
            for (int first = 0; first < parts.size(); first++) {
                StringBuilder joined = new StringBuilder();
                for (int last = first; last < parts.size() && joined.length() < VIN_LENGTH; last++) {
                    joined.append(text, parts.get(last)[0], parts.get(last)[1]);
                    if (joined.length() == VIN_LENGTH && last > first
                            && joined.chars().anyMatch(Character::isDigit)
                            && joined.chars().anyMatch(Character::isLetter)) {
                        return Optional.of(new Span(parts.get(first)[0], parts.get(last)[1]));
                    }
                }
            }
        }
        return Optional.empty();
    }

    public record Finding(PolicyCode code, String honeytokenId) {

        public Finding {
            Objects.requireNonNull(code, "code");
        }
    }

    /** Shortest first or last piece of a text honeytoken assembled across values (A-57). */
    static final int MIN_EDGE_PIECE = 3;

    /** A text honeytoken in the form it is matched in ({@link TextNormalizer#skeleton}). */
    private record TextToken(String id, String skeleton) {
    }

    private final List<Honeytoken> honeytokens;
    private final List<TextToken> textTokens;

    public SensitiveDataDetector() {
        this(Honeytoken.syntheticRegistry());
    }

    public SensitiveDataDetector(List<Honeytoken> honeytokens) {
        this.honeytokens = List.copyOf(honeytokens);
        this.textTokens = this.honeytokens.stream()
                .filter(token -> token.kind() == Honeytoken.Kind.TEXT)
                .map(token -> new TextToken(token.id(), TextNormalizer.skeleton(token.value())))
                .toList();
    }

    /** Honeytokens first (most specific), then patterns; on the text and on one decoded layer. */
    public Optional<Finding> detect(String text) {
        Optional<Finding> honeytoken = honeytoken(text);
        if (honeytoken.isPresent()) {
            return honeytoken;
        }
        List<String> forms = new ArrayList<>();
        forms.add(text);
        forms.addAll(OneLayerDecoder.decodedVariants(text));
        for (String form : forms) {
            Optional<Finding> pattern = pattern(TextNormalizer.canonical(form));
            if (pattern.isPresent()) {
                return pattern;
            }
        }
        return Optional.empty();
    }

    /** Sentinels in the text itself or in one decoded layer, in every numeric projection. */
    public Optional<Finding> honeytoken(String text) {
        Optional<Finding> direct = honeytokenInForm(text);
        if (direct.isPresent()) {
            return direct;
        }
        for (String decoded : OneLayerDecoder.decodedVariants(text)) {
            Optional<Finding> hit = honeytokenInForm(decoded);
            if (hit.isPresent()) {
                return hit;
            }
        }
        return Optional.empty();
    }

    /**
     * SEC03-F3 (A-58): honeytokens in what a model answered. Text tokens as everywhere (skeleton per value and one
     * decoded layer, then assembled across values, A-57); digit tokens only on the normalized and separator-collapsed
     * forms, without the all-digits and spelled-digit projections or a digit join across values, because an answer
     * routinely carries numbered steps ("1. … 2. … 9.") that those projections turn into a sentinel (security review
     * of A-58).
     */
    public Optional<Finding> honeytokenInAnswer(List<String> values) {
        for (String value : values) {
            List<String> forms = new ArrayList<>();
            forms.add(value);
            forms.addAll(OneLayerDecoder.decodedVariants(value));
            for (String form : forms) {
                String normalized = TextNormalizer.forMatching(form);
                Optional<Finding> digit =
                        digitTokenIn(List.of(normalized, TextNormalizer.collapseDigitSeparators(normalized)));
                if (digit.isPresent()) {
                    return digit;
                }
                Optional<Finding> text = textTokenIn(form);
                if (text.isPresent()) {
                    return text;
                }
            }
        }
        List<String> pieces = values.stream().map(TextNormalizer::skeleton).filter(piece -> !piece.isEmpty()).toList();
        return textTokens.stream()
                .filter(token -> assembles(token.skeleton(), pieces))
                .findFirst()
                .map(token -> new Finding(PolicyCode.HONEYTOKEN, token.id()));
    }

    /**
     * Safety net over the whole raw payload: normalized and separator-collapsed forms only (text
     * tokens: the look-alike skeleton, which also joins a JSON name to the next value; a false
     * positive there needs a value that ends a token and a name that starts it). The
     * all-digits projection is not applied here, because it would join digits of identifiers and
     * structure (false positives); values are covered per segment and by {@link #splitHoneytoken}.
     */
    public Optional<Finding> honeytokenInRawPayload(String rawText) {
        String normalized = TextNormalizer.forMatching(rawText);
        Optional<Finding> digit = digitTokenIn(List.of(normalized, TextNormalizer.collapseDigitSeparators(normalized)));
        if (digit.isPresent()) {
            return digit;
        }
        return textTokenIn(rawText);
    }

    /**
     * Sentinels split across fields or messages (used for the whole payload, not per segment): the
     * all-digits projection of the concatenated values for digit tokens, and (SEC03-F4, A-57) for
     * text tokens an assembly of look-alike skeletons, so "ZZHoney" in one history message and
     * "CustomerA" in the next, or half in customer text and half in a catalog string, are one token.
     * The assembly ignores payload order, because fixed server values sit between the pieces (the
     * message {@code role}, the tool name, SKU and OE before a category). Residuals (A-57): filler
     * inside a value between the pieces, and pieces sent in separate requests (no cross-request
     * window, A-46).
     */
    public Optional<Finding> splitHoneytoken(List<String> values) {
        StringBuilder digits = new StringBuilder();
        for (String value : values) {
            digits.append(TextNormalizer.digitsOnly(TextNormalizer.forMatching(value)));
        }
        Optional<Finding> digit = digitTokenIn(List.of(digits.toString()));
        if (digit.isPresent()) {
            return digit;
        }
        List<String> pieces = values.stream().map(TextNormalizer::skeleton).filter(piece -> !piece.isEmpty()).toList();
        return textTokens.stream()
                .filter(token -> assembles(token.skeleton(), pieces))
                .findFirst()
                .map(token -> new Finding(PolicyCode.HONEYTOKEN, token.id()));
    }

    /**
     * True if the token is one piece's suffix, then any number of whole pieces, then another piece's
     * prefix (or lies inside one piece). Pieces may come in any order and may repeat. The suffix and
     * the prefix are at least {@value #MIN_EDGE_PIECE} characters, so a fixed server value such as the
     * message role {@code CUSTOMER} cannot complete a token from a one- or two-letter edge (security
     * review of A-57); a split with a shorter edge is a residual.
     */
    static boolean assembles(String token, List<String> pieces) {
        int length = token.length();
        boolean[] reached = new boolean[length];
        for (String piece : pieces) {
            if (piece.contains(token)) {
                return true;
            }
            for (int end = MIN_EDGE_PIECE; end < length && end <= piece.length(); end++) {
                if (piece.endsWith(token.substring(0, end))) {
                    reached[end] = true;
                }
            }
        }
        for (int at = 1; at < length; at++) {
            if (!reached[at]) {
                continue;
            }
            String rest = token.substring(at);
            for (String piece : pieces) {
                if (rest.length() >= MIN_EDGE_PIECE && piece.startsWith(rest)) {
                    return true;
                }
                if (piece.length() < rest.length() && rest.startsWith(piece)) {
                    reached[at + piece.length()] = true;
                }
            }
        }
        return false;
    }

    private Optional<Finding> honeytokenInForm(String form) {
        String normalized = TextNormalizer.forMatching(form);
        List<String> numeric = new ArrayList<>();
        numeric.add(normalized);
        numeric.add(TextNormalizer.collapseDigitSeparators(normalized));
        numeric.add(TextNormalizer.digitsOnly(normalized));
        numeric.add(TextNormalizer.spelledDigits(normalized));
        numeric.addAll(TextNormalizer.scientificExpansions(normalized));
        Optional<Finding> digit = digitTokenIn(numeric);
        if (digit.isPresent()) {
            return digit;
        }
        return textTokenIn(form);
    }

    /**
     * SEC03-F1 (A-55): text honeytokens compared on the look-alike skeleton of both sides, so a Cyrillic or Greek
     * letter, a digit used as a letter, an accent or any separator inside the token does not hide it.
     */
    private Optional<Finding> textTokenIn(String text) {
        String skeleton = TextNormalizer.skeleton(text);
        return textTokens.stream()
                .filter(token -> skeleton.contains(token.skeleton()))
                .findFirst()
                .map(token -> new Finding(PolicyCode.HONEYTOKEN, token.id()));
    }

    private Optional<Finding> digitTokenIn(List<String> projections) {
        for (Honeytoken token : honeytokens) {
            if (token.kind() != Honeytoken.Kind.DIGITS) {
                continue;
            }
            for (String projection : projections) {
                if (projection.contains(token.value())) {
                    return Optional.of(new Finding(PolicyCode.HONEYTOKEN, token.id()));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Finding> pattern(String canonical) {
        String lower = canonical.toLowerCase(Locale.ROOT);
        if (EMAIL.matcher(lower).find()) {
            return Optional.of(new Finding(PolicyCode.EMAIL, null));
        }
        if (PHONE.matcher(lower).find()) {
            return Optional.of(new Finding(PolicyCode.PHONE, null));
        }
        if (MONEY_SUFFIX.matcher(lower).find() || MONEY_PREFIX.matcher(lower).find()) {
            return Optional.of(new Finding(PolicyCode.MONEY, null));
        }
        if (FULL_VIN.matcher(canonical).find() || splitVin(canonical).isPresent()) {
            return Optional.of(new Finding(PolicyCode.FULL_VIN, null));
        }
        return Optional.empty();
    }
}
