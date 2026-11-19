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
        LINK,
        SERVER_TOKEN,
        MONEY,
        STOCK_NUMBER,
        LONG_NUMBER,
        UNKNOWN_PART_CODE,
        ORDER_CLAIM
    }

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\n\\r\\t]]|[\\p{Cf}]");
    private static final Pattern LINK = Pattern.compile(
            "(?i)(\\b[a-z][a-z0-9+.-]*://|\\bwww\\.|\\b[a-z0-9-]+\\.(com|vn|net|org|io|info|biz|xyz|ru|cn|top|app|dev|me)\\b"
                    + "|mailto:|\\[[^\\]]*\\]\\([^)]*\\))");
    private static final Pattern SERVER_TOKEN = Pattern.compile("(?i)\\b(pr|qh|vin|cref|usr|cv)_[A-Za-z0-9_-]{4,}");
    private static final Pattern MONEY = Pattern.compile(
            "(?iu)(\\d[\\d.,\\s]*\\s*(₫|đ(?!\\p{L})|vnd|vnđ|đồng|dong\\b|nghìn|nghin|ngàn|ngan|triệu|trieu|tr\\b|k\\b|usd|\\$|€))"
                    + "|((₫|\\$|€|vnd|usd)\\s*\\d)"
                    + "|((giá|gia|price|tổng|tong|thành tiền|thanh tien|chiết khấu|chiet khau|giảm|giam)\\D{0,20}\\d)");
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
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        if (normalized.length() > MAX_CHARS) {
            return Optional.of(Rejection.TOO_LONG);
        }
        if (CONTROL.matcher(normalized).find()) {
            return Optional.of(Rejection.CONTROL_CHARACTERS);
        }
        if (LINK.matcher(normalized).find()) {
            return Optional.of(Rejection.LINK);
        }
        if (SERVER_TOKEN.matcher(normalized).find()) {
            return Optional.of(Rejection.SERVER_TOKEN);
        }
        if (ORDER_CLAIM.matcher(normalized).find()) {
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
        if (MONEY.matcher(rest).find()) {
            return Optional.of(Rejection.MONEY);
        }
        if (STOCK.matcher(rest).find()) {
            return Optional.of(Rejection.STOCK_NUMBER);
        }
        if (LONG_NUMBER.matcher(rest).find()) {
            return Optional.of(Rejection.LONG_NUMBER);
        }
        return Optional.empty();
    }
}
