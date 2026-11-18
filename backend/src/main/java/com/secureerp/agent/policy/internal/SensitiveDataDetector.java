package com.secureerp.agent.policy.internal;

import com.secureerp.agent.policy.PolicyCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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
     */
    static final Pattern MONEY_PREFIX = Pattern.compile(
            "(?U)(?:(?<!\\w)(?<!quoc )(?<!quốc )(?<!tham )(?:giá|gia|price|tổng|tong|total|thành tiền|thanh tien|vốn|von|cost|công nợ|cong no|nợ|debt|hạn mức|han muc)(?!\\w)"
                    + "(?:\\s*(?:là|la|khoảng|khoang|chỉ|chi|tầm|tam|cộng|cong|vốn|von|bán|ban|mới|moi|[:=]))*\\s*|[₫$]\\s?)"
                    + "\\d(?:[\\d.,' _]*\\d){3,}");
    /** VN mobile (10 digits) or landline (11 digits); not inside a longer alphanumeric token such as a UUID. */
    static final Pattern PHONE = Pattern.compile(
            "(?<![\\w+])(?:\\+?84|0)[\\s.\\-]?[35789](?:[\\s.\\-]?\\d){8}(?!\\w)|(?<![\\w+])(?:\\+?84|0)[\\s.\\-]?2(?:[\\s.\\-]?\\d){9}(?!\\w)");
    static final Pattern EMAIL = Pattern.compile("[a-z0-9._%+-]+@[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}",
            Pattern.CASE_INSENSITIVE);
    /**
     * 17 VIN characters (no I, O, Q) with at least one digit and one letter, not inside a longer
     * token. "_" and "-" also count as token characters, so the tail of "vin_..." or a "qh_..."
     * handle is not read as a VIN (security review M3).
     */
    static final Pattern FULL_VIN = Pattern.compile(
            "(?<![A-Za-z0-9_-])(?=[A-HJ-NPR-Za-hj-npr-z0-9]{0,16}\\d)(?=[A-HJ-NPR-Za-hj-npr-z0-9]{0,16}[A-HJ-NPR-Za-hj-npr-z])"
                    + "[A-HJ-NPR-Za-hj-npr-z0-9]{17}(?![A-Za-z0-9_-])");

    public record Finding(PolicyCode code, String honeytokenId) {

        public Finding {
            Objects.requireNonNull(code, "code");
        }
    }

    private final List<Honeytoken> honeytokens;

    public SensitiveDataDetector() {
        this(Honeytoken.syntheticRegistry());
    }

    public SensitiveDataDetector(List<Honeytoken> honeytokens) {
        this.honeytokens = List.copyOf(honeytokens);
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
     * Safety net over the whole raw payload: normalized and separator-collapsed forms only. The
     * all-digits projection is not applied here, because it would join digits of identifiers and
     * structure (false positives); values are covered per segment and by {@link #splitHoneytoken}.
     */
    public Optional<Finding> honeytokenInRawPayload(String rawText) {
        String normalized = TextNormalizer.forMatching(rawText);
        Optional<Finding> digit = digitTokenIn(List.of(normalized, TextNormalizer.collapseDigitSeparators(normalized)));
        if (digit.isPresent()) {
            return digit;
        }
        String compact = normalized.replaceAll("\\s+", "");
        return honeytokens.stream()
                .filter(token -> token.kind() == Honeytoken.Kind.TEXT && compact.contains(token.value()))
                .findFirst()
                .map(token -> new Finding(PolicyCode.HONEYTOKEN, token.id()));
    }

    /**
     * Sentinels split across fields or messages: checks only the all-digits projection of the
     * concatenated values (used for the whole payload, not per segment).
     */
    public Optional<Finding> splitHoneytoken(List<String> values) {
        StringBuilder digits = new StringBuilder();
        for (String value : values) {
            digits.append(TextNormalizer.digitsOnly(TextNormalizer.forMatching(value)));
        }
        return digitTokenIn(List.of(digits.toString()));
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
        String compact = normalized.replaceAll("\\s+", "");
        for (Honeytoken token : honeytokens) {
            if (token.kind() == Honeytoken.Kind.TEXT && compact.contains(token.value())) {
                return Optional.of(new Finding(PolicyCode.HONEYTOKEN, token.id()));
            }
        }
        return Optional.empty();
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
        if (FULL_VIN.matcher(canonical).find()) {
            return Optional.of(new Finding(PolicyCode.FULL_VIN, null));
        }
        return Optional.empty();
    }
}
