package com.secureerp.agent.agent.internal;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the model's final text before a customer sees it (AI_GATEWAY §4, rule 30 §6, T-GW-OUTPUT,
 * T-UC02-OE-FABRICATED). Model output is untrusted; anything that looks like a price, a stock number, a
 * part code not returned by a tool in this turn, a link, a server token or a claim that an order was
 * approved is rejected as a whole (decision 2026-11-19: no partial repair). DESIGN PROPOSAL: regex rules
 * are a second layer; the type gate keeps prices and stock numbers away from the model in the first
 * place, so these rules mainly catch invention and injection echoes.
 */
final class OutputValidator {

    /** Longest text a customer gets from the model (ModelResponse allows 4000; the chat shows less). */
    static final int MAX_CHARS = 2000;

    enum Rejection {
        EMPTY,
        TOO_LONG,
        CONTROL_CHARACTERS,
        CONFUSABLE_SCRIPT,
        LINK,
        SERVER_TOKEN,
        MONEY,
        STOCK_NUMBER,
        LONG_NUMBER,
        UNKNOWN_PART_CODE,
        ORDER_CLAIM
    }

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\n\\r\\t]]|[\\p{Cf}]");
    /** Emoji variation selectors U+FE0E/U+FE0F: allowed, but dropped before matching so they cannot split a word. */
    private static final Pattern VARIATION_SELECTOR = Pattern.compile("[\\uFE0E\\uFE0F]");
    /**
     * OUTPUT-CONFUSABLES (A-77, 1A/2A): after NFKC, any letter outside ASCII and the Vietnamese alphabet (Cyrillic
     * "к", Greek "ο", small capital "ᴋ", dotless "ı", eth "Ð" for "Đ"…), any digit outside 0-9 ("٢") and any
     * combining mark left over (one that did not compose, such as a strikethrough inside "triệu") would slip past the
     * keyword rules below, so the answer is rejected as a whole. Vietnamese letters: Latin-1 vowels with grave, acute,
     * circumflex or tilde; ă đ ĩ ũ; ơ ư; and the precomposed block U+1EA0–U+1EF9. A-77 review security H1/L1/L2: also
     * characters that show as a gap or a box but are no space for the rules (Braille blank U+2800, private use,
     * unassigned code points, a lone surrogate) and the CJK full stops that a browser may read as a dot in a link.
     * Other Latin letters and symbols that NFKC turns into Greek ("Škoda", "Citroën", "Ø", "µm", "kΩ") are accepted
     * false positives: the bot answers in Vietnamese or English, and a blocked answer goes to staff.
     */
    private static final Pattern CONFUSABLE = Pattern.compile(
            "[\\p{L}&&[^A-Za-z\\u00C0-\\u00C3\\u00C8-\\u00CA\\u00CC\\u00CD\\u00D2-\\u00D5\\u00D9\\u00DA\\u00DD"
                    + "\\u00E0-\\u00E3\\u00E8-\\u00EA\\u00EC\\u00ED\\u00F2-\\u00F5\\u00F9\\u00FA\\u00FD"
                    + "\\u0102\\u0103\\u0110\\u0111\\u0128\\u0129\\u0168\\u0169\\u01A0\\u01A1\\u01AF\\u01B0"
                    + "\\u1EA0-\\u1EF9]]"
                    + "|[\\p{N}&&[^0-9]]|\\p{M}|[\\p{Co}\\p{Cn}\\p{Cs}\\u2800\\u3002\\uFF61]");
    private static final Pattern LINK = Pattern.compile(
            "(?i)(\\b[a-z][a-z0-9+.-]*://|\\bwww\\.|\\b[a-z0-9-]+\\.(com|vn|net|org|io|info|biz|xyz|ru|cn|top|app|dev|me)\\b"
                    + "|mailto:|\\[[^\\]]*\\]\\([^)]*\\))");
    private static final Pattern SERVER_TOKEN = Pattern.compile("(?i)\\b(pr|qh|vin|cref|usr|cv)_[A-Za-z0-9_-]{4,}");
    // OUTPUT-MONEY-WORDS (A-76): money units as whole words, without the part and measure words that start the same
    // way: "đồng hồ" (gauge), "tỷ lệ" (ratio), "vạn năng" (universal joint), "củ đề"/"củ phát" (starter, alternator),
    // and a large unit followed by a distance or a speed ("năm nghìn km", "hai nghìn vòng").
    private static final String NOT_A_MEASURE = "(?!\\s*(km|cây số|cay so|kilomet|kilômét|giờ|gio|vòng|vong)(?!\\p{L}))";
    private static final String LARGE_UNIT = "(?:(?:nghìn|nghin|ngàn|ngan|triệu|trieu)(?!\\p{L})" + NOT_A_MEASURE
            + "|(?:tỷ|tỉ|ty)(?!\\p{L})(?!\\s*(lệ|le|số|so)(?!\\p{L}))"
            + "|vạn(?!\\p{L})(?!\\s*(năng|nang)(?!\\p{L}))" + NOT_A_MEASURE + ")";
    private static final String DONG = "(?:đồng|dong)(?!\\p{L})(?!\\s*(hồ|ho|bộ|bo|nghĩa|nghia|thời|thoi)(?!\\p{L}))";
    private static final String CU = "củ(?!\\p{L})(?!\\s*(đề|de|phát|phat)(?!\\p{L}))";
    private static final String ENGLISH_UNIT = "(?:million|mil|thousand|grand|billion|bn)(?!\\p{L})";
    private static final Pattern MONEY = Pattern.compile(
            // Possessive run (A-76 review: "1-1-1-…" took 0.5 s with backtracking through every unit).
            "(?iu)(\\d[\\d.,\\s\\-_]*+(₫|đ(?!\\p{L})|vnd|vnđ|usd|\\$|€|" + DONG + "|" + LARGE_UNIT + "|" + CU + "|"
                    + ENGLISH_UNIT
                    // A-76 review security M5: "tr"/"k" as whole words ("\\b" is ASCII-only, so "2 trục", "4 kỳ" are
                    // not amounts); "5 trăm" stays blocked; slang "1tr25", "1m2", "500 cành".
                    + "|tr\\.?(?!\\p{L})|tr(?=\\d)|trăm(?!\\p{L})|tram(?!\\p{L})|k(?!\\p{L})|m(?=\\d)|cành(?!\\p{L})))"
                    + "|((₫|\\$|€|vnd|usd)\\s*\\d)"
                    // A-78: "gía" (the tone on the i, a common typo and no word) with the other price words.
                    + "|((giá|gía|gìa|gỉa|gĩa|gịa|gia|price|tổng|tong|thành tiền|thanh tien|chiết khấu|chiet khau|giảm|giam)\\D{0,20}\\d)");
    /** OUTPUT-MONEY-WORDS (A-76): a Vietnamese or English number word, with or without diacritics, as a whole word. */
    private static final String NUMBER_WORD = "(?:một|mốt|mot|hai|ba|bốn|bon|tư|năm|nam|lăm|lam|nhăm|sáu|sau|bảy|bẩy|bay"
            + "|tám|tam|chín|chin|mười|muoi|mươi|linh|lẻ|trăm|tram|rưỡi|rưởi|ruoi|nửa|chục|chuc"
            + "|one|two|three|four|five|six|seven|eight|nine|ten|twenty|fifty|hundred)(?!\\p{L})";
    /** Between number words and units: spaces, and the hyphens, brackets and quotes a model could slip in (review M1). */
    private static final String SEP = "[\\s\\-_–—()\"'“”]{1,3}";
    /** A count, a time or a measure after a number: "hai bộ", "ba năm", "hai lần". */
    private static final String COUNT = "(?!" + SEP + "(bộ|bo|cái|cai|chiếc|chiec|món|mon|loại|loai|ngày|ngay|tuần|tuan"
            + "|tháng|thang|năm|nam|giờ|gio|phút|phut|người|nguoi|lần|lan|km|lít|lit)(?!\\p{L}))";
    /**
     * OUTPUT-MONEY-WORDS (A-76, decisions 1A–3A): amounts in words. (1) Number words followed by a money unit ("hai
     * triệu", "năm trăm nghìn", "mot ty", "hai mươi k", "two million"); (2) a large unit followed by a number word
     * ("triệu rưỡi"); (3) a price word followed, within the same sentence, by at least two number words that are not a
     * count ("giá một bộ là hai trăm rưởi", not "tổng cộng hai bộ" or "giá năm nay"). Biased to fail closed ("một triệu
     * cảm ơn" is blocked); number words alone ("ba trăm rưởi") are not, they read like a quantity.
     */
    private static final Pattern MONEY_WORDS = Pattern.compile(
            "(?iu)((?<!\\p{L})" + NUMBER_WORD + "(" + SEP + NUMBER_WORD + ")*" + SEP
                    + "(" + LARGE_UNIT + "|" + DONG + "|" + CU + "|" + ENGLISH_UNIT + "|tr\\.?(?!\\p{L})|k(?!\\p{L})))"
                    + "|((?<!\\p{L})(triệu|trieu|tỷ|tỉ|nghìn|nghin|ngàn|ngan)" + SEP + NUMBER_WORD + ")"
                    + "|((?<!\\p{L})(giá|gía|gìa|gỉa|gĩa|gịa|gia|tổng|tong|thành tiền|thanh tien|chiết khấu|chiet khau|giảm|giam|chi phí|chi phi"
                    + "|phí|price|cost)(?!\\p{L})(?!\\s*(trị|tri)(?!\\p{L}))[^.!?\\n]{0,30}?(?<!\\p{L})"
                    + NUMBER_WORD + "(" + SEP + NUMBER_WORD + ")++" + COUNT + ")");
    private static final Pattern STOCK = Pattern.compile(
            "(?iu)((còn|con|tồn|ton|stock|kho)\\s*(lại|lai|kho|hàng|hang)?\\s*:?\\s*\\d)|(\\d+\\s*(cái|cai|chiếc|chiec|bộ|bo)"
                    + "\\s*(trong kho|tồn|ton|còn lại|con lai))");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\d[\\d.,\\s]{3,}\\d");
    /** Upper-case codes mixing letters and digits, digit-dash codes, or three dash-joined groups (OE/SKU shaped). */
    private static final Pattern PART_CODE = Pattern.compile(
            "\\b(?=[A-Z0-9.-]*[0-9])(?=[A-Z0-9.-]*[A-Z])[A-Z0-9][A-Z0-9.-]{3,30}[A-Z0-9]\\b|\\b\\d{3,}-[A-Z0-9-]+\\b"
                    + "|\\b[A-Z0-9]{2,}(?:-[A-Z0-9]+){2,}\\b");
    private static final Pattern ORDER_CLAIM = Pattern.compile(
            "(?iu)((đã|da|được|duoc)\\s+(duyệt|duyet|phê duyệt|phe duyet|xác nhận|xac nhan|tạo|tao|đặt|dat|chốt|chot)"
                    + "\\s+(đơn|don|order))|((đơn|don)( hàng| hang)?\\s+(đã|da)\\s+(được|duoc)?\\s*(duyệt|duyet|xác nhận|xac nhan"
                    + "|tạo|tao|đặt|dat|chốt|chot))|(order\\s+(has\\s+been\\s+|is\\s+)?(approved|confirmed|placed|created))"
                    + "|(approved\\s+(your\\s+)?order)");

    // OUTPUT-DIACRITIC-VARIANTS (A-78): the same rules on a copy without diacritics (đ read as d, runs of spaces read as
    // one), so a misspelling a reader still understands ("5 triêu", "đơn hàng đã duỵêt") is caught. Only keywords whose
    // form without diacritics is not a common word (3A, the rest are documented gaps): left out are "dong" (dòng, động,
    // đông), "tram" (trạm), "ngan" (ngăn, ngắn), "ty" (tý), "van" (van, vặn), "canh" (cạnh), "cu" (cũ); "ton" (tốn,
    // tôn), "con" (côn, cồn) and "kho" (khó, khô) alone; the price words "gia" (giả, già), "tong" (tông), "giam"
    // (giám), "phi"; the order verb "dat" (đạt: "đã đạt đơn tối thiểu"); and, after a price word, the number words
    // that are also common words ("từ", "làm", "nữa", "tạm", "sau", "bay", "lẻ", "linh", "chúc"). "da" needs a word
    // before it, so the polite "Dạ, chốt đơn ở nút bên dưới" is not a claim. The copy only holds ASCII letters
    // (CONFUSABLE_SCRIPT runs first), so word ends are ASCII.
    private static final Pattern DIACRITIC = Pattern.compile("\\p{M}");
    private static final Pattern SPACE_RUN = Pattern.compile("[ \\t]{2,}");
    private static final String A_NUMBER_WORD = "(?:mot|hai|ba|bon|tu|nam|lam|nham|sau|bay|tam|chin|muoi|linh|le|tram"
            + "|ruoi|nua|chuc)(?![a-z])";
    private static final String A_PRICE_NUMBER_WORD = "(?:mot|hai|ba|bon|nam|nham|chin|muoi|tram|ruoi)(?![a-z])";
    private static final String A_LARGE_UNIT = "(?:trieu|nghin)(?![a-z])(?!\\s*(km|cay so|kilomet|gio|vong)(?![a-z]))";
    private static final String A_COUNT = "(?!" + SEP + "(bo|cai|chiec|mon|loai|ngay|tuan|thang|nam|gio|phut|nguoi|lan"
            + "|km|lit)(?![a-z]))";
    private static final Pattern STRIPPED_MONEY = Pattern.compile(
            "(?i)(\\d[\\d.,\\s\\-_]*+" + A_LARGE_UNIT + ")"
                    + "|((?<![a-z])" + A_NUMBER_WORD + "(" + SEP + A_NUMBER_WORD + ")*" + SEP + A_LARGE_UNIT + ")"
                    + "|((?<![a-z])(trieu|nghin)" + SEP + A_NUMBER_WORD + ")"
                    + "|((?<![a-z])(thanh tien|chiet khau)(?![a-z])\\D{0,20}\\d)"
                    + "|((?<![a-z])(thanh tien|chiet khau|chi phi)(?![a-z])"
                    + "[^.!?\\n]{0,30}?(?<![a-z])" + A_PRICE_NUMBER_WORD + "(" + SEP + A_PRICE_NUMBER_WORD + ")++"
                    + A_COUNT + ")");
    private static final Pattern STRIPPED_ORDER_CLAIM = Pattern.compile(
            "(?i)(((?<=[a-z] )da|(?<![a-z])duoc)\\s+(duyet|phe duyet|xac nhan|tao|chot)\\s+(don|order)(?![a-z]))"
                    + "|((?<![a-z])don( hang)?\\s+da\\s+(duoc\\s+)?(duyet|phe duyet|xac nhan|tao|chot)(?![a-z]))");
    private static final Pattern STRIPPED_STOCK = Pattern.compile(
            "(?i)((?<![a-z])(con lai|con hang|ton kho|kho con|kho hang)\\s*:?\\s*\\d)"
                    + "|(\\d+\\s*(cai|chiec|bo)\\s*(trong kho|ton kho|con lai)(?![a-z]))");

    private OutputValidator() {
    }

    /**
     * @param allowedCodes SKU and OE codes returned by tools in this turn (upper case); a code in the text
     *                     must be one of them
     */
    static Optional<Rejection> check(String text, Set<String> allowedCodes) {
        if (text == null || text.isBlank()) {
            return Optional.of(Rejection.EMPTY);
        }
        // A-76 review security M1: every Unicode separator (line and paragraph separators, no-break spaces) counts as a
        // space, so "hai triệu" is read like "hai triệu".
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).replaceAll("\\p{Z}", " ");
        // OUTPUT-LENGTH-RAW (A-77, 3A): the customer gets the original text, so its length counts too; NFKC can make a
        // text shorter (decomposed Vietnamese) or longer (ligatures).
        if (text.length() > MAX_CHARS || normalized.length() > MAX_CHARS) {
            return Optional.of(Rejection.TOO_LONG);
        }
        if (CONTROL.matcher(normalized).find()) {
            return Optional.of(Rejection.CONTROL_CHARACTERS);
        }
        normalized = VARIATION_SELECTOR.matcher(normalized).replaceAll("");
        if (CONFUSABLE.matcher(normalized).find()) {
            return Optional.of(Rejection.CONFUSABLE_SCRIPT);
        }
        if (LINK.matcher(normalized).find()) {
            return Optional.of(Rejection.LINK);
        }
        if (SERVER_TOKEN.matcher(normalized).find()) {
            return Optional.of(Rejection.SERVER_TOKEN);
        }
        if (ORDER_CLAIM.matcher(normalized).find()
                || STRIPPED_ORDER_CLAIM.matcher(withoutDiacritics(normalized)).find()) {
            return Optional.of(Rejection.ORDER_CLAIM);
        }
        // Part codes first: an allowed code may contain digit runs, which must not count as numbers.
        StringBuilder withoutCodes = new StringBuilder();
        Matcher codes = PART_CODE.matcher(normalized);
        while (codes.find()) {
            if (!allowedCodes.contains(codes.group().toUpperCase(Locale.ROOT))) {
                return Optional.of(Rejection.UNKNOWN_PART_CODE);
            }
            codes.appendReplacement(withoutCodes, " ");
        }
        codes.appendTail(withoutCodes);
        String rest = withoutCodes.toString();
        String restWithoutDiacritics = withoutDiacritics(rest);
        if (MONEY.matcher(rest).find() || MONEY_WORDS.matcher(rest).find()
                || STRIPPED_MONEY.matcher(restWithoutDiacritics).find()) {
            return Optional.of(Rejection.MONEY);
        }
        if (STOCK.matcher(rest).find() || STRIPPED_STOCK.matcher(restWithoutDiacritics).find()) {
            return Optional.of(Rejection.STOCK_NUMBER);
        }
        if (LONG_NUMBER.matcher(rest).find()) {
            return Optional.of(Rejection.LONG_NUMBER);
        }
        return Optional.empty();
    }

    /** A-78: Vietnamese text without tone and vowel marks, đ read as d ("đơn hàng đã duỵêt" → "don hang da duyet"). */
    private static String withoutDiacritics(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        String stripped = DIACRITIC.matcher(decomposed).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
        return SPACE_RUN.matcher(stripped).replaceAll(" ");
    }
}
