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
    /**
     * OUTPUT-ENGLISH-MONEY (A-84, 1A): currencies written as words or codes, as whole words, with a country before
     * dollars ("45 US dollars", review security H2). Not "pound(s)": "45 pounds" may be a weight. Not "euro" before a
     * digit: "Euro 5, Euro 6 engines" is an emission standard (review security).
     */
    private static final String CURRENCY_WORD = "(?:(?:us|u\\.s\\.?|american|australian|canadian|singapore|hk)\\s*)?"
            + "(?:dollars?|bucks?|euros?(?!\\s*\\d)|eur|cents?|gbp|yen|jpy)(?!\\p{L})";
    private static final Pattern MONEY = Pattern.compile(
            // Possessive run (A-76 review: "1-1-1-…" took 0.5 s with backtracking through every unit).
            "(?iu)(\\d[\\d.,\\s\\-_]*+(₫|đ(?!\\p{L})|vnd|vnđ|usd|\\$|€|£|¥|₹|₩|¢|" + DONG + "|" + LARGE_UNIT + "|" + CU + "|"
                    + ENGLISH_UNIT + "|" + CURRENCY_WORD
                    // A-76 review security M5: "tr"/"k" as whole words ("\\b" is ASCII-only, so "2 trục", "4 kỳ" are
                    // not amounts); "5 trăm" stays blocked; slang "1tr25", "1m2", "500 cành".
                    + "|tr\\.?(?!\\p{L})|tr(?=\\d)|trăm(?!\\p{L})|tram(?!\\p{L})|k(?!\\p{L})|m(?=\\d)|cành(?!\\p{L})))"
                    + "|((₫|\\$|€|£|¥|₹|₩|vnd|usd|(?<!\\p{L})(eur|gbp|jpy|dollars?|bucks?)(?!\\p{L}))\\s*:?\\s*\\d)"
                    // A-84 (2A): English price words as whole words, not after a hyphen ("low-cost") nor before a
                    // hyphen and a letter ("cost-effective"; "Cost-45" is blocked), then a digit in the same sentence
                    // as below, within 40 characters (English sentences run longer, review QA M2). "Discount 10%" is
                    // blocked: a discount is price information. Not total, amount, pay, charge, worth: they often count
                    // ("Total 2 sets"). Also "priced", "pricing", "prices" ("price" is below; review security M).
                    + "|((?<![\\p{L}\\-])(cost|costs|costing|subtotal|fee|fees|discount|priced|pricing|prices)(?!\\p{L})"
                    + "(?!-\\p{L})(?:[^\\d.!?\\n]|[.!?\\n](?!\\s*(?-i:\\p{Lu}))){0,40}\\d)"
                    // A-78: "gía" (the tone on the i, a common typo and no word) with the other price words.
                    // A-79 (OUTPUT-PRICE-WORD-BOUNDARY): a price word is a whole word ("gian", "giám", "giao", "giai",
                    // "giáp" are not), "giảm xóc"/"giảm chấn" (shock absorber, damper) are part names, and the digit is
                    // in the same sentence: a full stop, "!", "?" or a line break ends it only when an upper-case
                    // letter starts the next one ("Giá hỏi nhân viên. Xe 2 cầu"), so "Giá. 500", "Giá?950" and
                    // "Giá:\n950" stay blocked (review security M2). Kept, fail closed: "gia", "tong", "giam" without
                    // diacritics ("tham gia 2…"), "tổng" ("tổng đài 1900"), "giảm thanh"/"giảm tốc" ("giảm thanh lý
                    // 30%" is a discount, review security M1).
                    + "|((?<!\\p{L})(giá|gía|gìa|gỉa|gĩa|gịa|gia|price|tổng|tong|thành tiền|thanh tien|chiết khấu"
                    + "|chiet khau|(giảm|giam)(?!\\s*(xóc|xoc|chấn|chan)(?!\\p{L})))(?!\\p{L})"
                    + "(?:[^\\d.!?\\n]|[.!?\\n](?!\\s*(?-i:\\p{Lu}))){0,20}\\d)");
    /** OUTPUT-MONEY-WORDS (A-76): a Vietnamese or English number word, with or without diacritics, as a whole word. */
    private static final String NUMBER_WORD = "(?:một|mốt|mot|hai|ba|bốn|bon|tư|năm|nam|lăm|lam|nhăm|sáu|sau|bảy|bẩy|bay"
            + "|tám|tam|chín|chin|mười|muoi|mươi|linh|lẻ|trăm|tram|rưỡi|rưởi|ruoi|nửa|chục|chuc"
            + "|one|two|three|four|five|six|seven|eight|nine|ten|twenty|fifty|hundred"
            // A-84 (3A): the other English teens and tens.
            + "|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|thirty|forty|sixty|seventy"
            + "|eighty|ninety)(?!\\p{L})";
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
                    + "(" + LARGE_UNIT + "|" + DONG + "|" + CU + "|" + ENGLISH_UNIT + "|" + CURRENCY_WORD
                    + "|tr\\.?(?!\\p{L})|k(?!\\p{L})))"
                    + "|((?<!\\p{L})(triệu|trieu|tỷ|tỉ|nghìn|nghin|ngàn|ngan)" + SEP + NUMBER_WORD + ")"
                    + "|((?<!\\p{L})(giá|gía|gìa|gỉa|gĩa|gịa|gia|tổng|tong|thành tiền|thanh tien|chiết khấu|chiet khau|giảm|giam|chi phí|chi phi"
                    // A-84 review security M: the English price words of MONEY too ("It costs forty-five").
                    + "|phí|price|cost|costs|costing|fee|fees|subtotal|discount|priced|pricing|prices)(?!\\p{L})(?!\\s*(trị|tri)(?!\\p{L}))[^.!?\\n]{0,30}?(?<!\\p{L})"
                    + NUMBER_WORD + "(" + SEP + NUMBER_WORD + ")++" + COUNT + ")");
    private static final Pattern STOCK = Pattern.compile(
            "(?iu)((còn|con|tồn|ton|stock|kho)\\s*(lại|lai|kho|hàng|hang)?\\s*:?\\s*\\d)|(\\d+\\s*(cái|cai|chiếc|chiec|bộ|bo)"
                    + "\\s*(trong kho|tồn|ton|còn lại|con lai))");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\d[\\d.,\\s]{3,}\\d");
    /** Upper-case codes mixing letters and digits, digit-dash codes, or three dash-joined groups (OE/SKU shaped). */
    private static final Pattern PART_CODE = Pattern.compile(
            "\\b(?=[A-Z0-9.-]*[0-9])(?=[A-Z0-9.-]*[A-Z])[A-Z0-9][A-Z0-9.-]{3,30}[A-Z0-9]\\b|\\b\\d{3,}-[A-Z0-9-]+\\b"
                    + "|\\b[A-Z0-9]{2,}(?:-[A-Z0-9]+){2,}\\b");
    // OUTPUT-ORDER-CLAIM-MORE (A-82): more verbs (2A) and up to 30 characters between "đơn" and "đã"/"được" in the same
    // sentence (1A). Not a claim (review security H1/H2 narrowed these to the words right after "đơn" or the verb):
    // "đơn (hàng) (của X)" followed by "chưa/sẽ/đang" and a verb or "chờ/đợi" ("Đơn của bạn chưa được duyệt, anh đã…",
    // "Đơn đang đợi dù anh đã xác nhận"); a condition right before "đơn" ("Nếu đơn đã được duyệt…"); a question
    // "…chưa?" right after "đơn" or the verb, one word allowed between ("Bạn đã xác nhận đơn chưa?"); "đơn giản", "đơn
    // vị", "đơn giá"… A passive claim needs "rồi"/"xong" without "đã" ("Đơn được duyệt rồi ạ"), and may name who did it
    // ("đã được bên em duyệt").
    private static final String ORDER_VERB = "(duyệt|duyet|phê duyệt|phe duyet|xác nhận|xac nhan|tạo|tao|đặt|dat|chốt|chot"
            + "|lên|len|ghi nhận|ghi nhan|hoàn tất|hoan tat|xử lý|xử lí|xu ly|xu li|giao|thanh toán|thanh toan)(?!\\p{L})";
    // A-83 review security M2: also "hệ thống", "quản lý", "admin", "kế toán".
    private static final String ORDER_AGENT = "((bên em|ben em|bên mình|ben minh|nhân viên|nhan vien|cửa hàng|cua hang|shop"
            + "|chúng tôi|chung toi|chúng em|chung em|hệ thống|he thong|quản lý|quan ly|admin|kế toán|ke toan|em)\\s+)?";
    private static final String NOT_AN_ORDER = "(?!\\s*(giản|gian|vị|vi|giá|gia|lẻ|le|độc|doc|thuần|thuan|điệu|dieu|sơ|so)"
            + "(?!\\p{L}))";
    private static final String NOT_DONE = "(?!(\\s+(hàng|hang))?(\\s+(của|cua)(\\s+\\p{L}+){1,2})?\\s+((chưa|chua|sẽ|se"
            + "|đang|dang)\\s+(được|duoc|chờ|cho|đợi|doi|" + ORDER_VERB + ")|(chờ|đợi)(?!\\p{L})"
            + "|(cho|doi)\\s+(duyệt|duyet|xử lý|xu ly|xu li|nhân viên|nhan vien|xác nhận|xac nhan)(?!\\p{L})))";
    private static final String QUESTION = "(?!(\\s+\\p{L}+)?\\s*,?\\s*(chưa|chua)(\\s+(ạ|a|nhỉ|nhi|vậy|vay))?\\s*\\?)";
    // OUTPUT-ORDER-CLAIM-REST (A-83, 3A): up to 60 characters between "đơn" and "đã" in the same sentence (was 30).
    private static final String ORDER_SUBJECT = "(?<!(nếu|neu|khi|lúc|luc|để)\\s)(?<!\\p{L})(đơn|don)(?!\\p{L})" + NOT_AN_ORDER
            + NOT_DONE + "[^.!?\\n]{0,60}?(?<!\\p{L})";
    /**
     * A-83 (4A): "đã" and the typos that keep the "đ" ("Đả duyệt đơn"); "dạ" has no "đ", so it is never one of them.
     * Not "đa" in "tối đa" ("Mỗi đơn tối đa giao 10 sản phẩm", review security L1).
     */
    private static final String DA_TYPO = "(?<!(tối|toi)\\s)(đã|đả|đá|đà|đạ|đa)";
    private static final String DA = "(" + DA_TYPO + "|da)";
    /** A-83 (1A): the verbs of a claim without "đơn"; not "ghi nhận", as "Em đã ghi nhận ạ" means "noted". */
    private static final String BARE_CLAIM_VERB = "(duyệt|duyet|phê duyệt|phe duyet|xác nhận|xac nhan|tạo|tao|đặt|dat"
            + "|chốt|chot|lên|len|hoàn tất|hoan tat|xử lý|xử lí|xu ly|xu li|giao|thanh toán|thanh toan)(?!\\p{L})";
    /**
     * A-83 (1A, review QA H2, security H2): a sentence starts at the start of the text, after . ! ? : ; or a line
     * break, and may begin with symbols (markdown, a bullet, a number, an emoji).
     */
    private static final String SENTENCE_START = "(?:^|[.!?:;\\n\\r])[^\\p{L}]*+";
    /**
     * A-83 (1A, review QA H1, security H1): up to five words after the verb up to the end of the clause, none of them
     * an object that makes it no order ("báo giá", "thông tin", "lại", "yêu cầu"…), a negation, a question or a
     * condition. The clause ends at anything that is not a letter, a digit or "?", or at the end of the text.
     */
    private static final String BARE_TAIL = "(\\s+(?!(báo|bao|thông|thong|lại|lai|yêu|yeu|tin|mã|ma|phụ|phu|xe|giá|gia|câu"
            + "|cau|lịch|lich|chưa|chua|không|khong|thì|thi|nếu|neu|khi|sau|để)(?!\\p{L}))\\p{L}+){0,5}"
            + "\\s*+([^\\p{L}\\d?\\s]|$)";
    /**
     * A-83 (1A): a claim without "đơn" ("Đã được duyệt rồi anh nhé"): at the start of a sentence, after "Dạ,"/"Vâng,"/
     * "Dạ anh," and "em"/"bên em"/"hệ thống"…, "đã (được)" or "được" and a verb ending the clause (see BARE_TAIL).
     * "Báo giá đã được tạo" (a subject first), "Đã tạo báo giá cho anh" (a safe object) and "Đã xác nhận chưa?" are no
     * claim. Plain "da" is left to the copy without diacritics, which needs a word before it ("Da xac nhan nhe").
     */
    private static final String BARE_CLAIM = SENTENCE_START
            + "(((dạ|vâng|vang|ok|okay|oke|ạ)(?!\\p{L})[\\s,]+|(anh|chị|chi|bạn|ban)\\s*,\\s*)){0,3}"
            + ORDER_AGENT + "(" + DA_TYPO + "\\s+((được|duoc)\\s+" + ORDER_AGENT + ")?|(được|duoc)\\s+" + ORDER_AGENT + ")"
            + BARE_CLAIM_VERB + BARE_TAIL;
    /** A-83 (2A, review security M1): "này/đó/hôm qua…", up to three words after "của", "đã được" before "ok". */
    private static final String DONE_WORD_SUBJECT = "(\\s+(hàng|hang))?(\\s+(này|nay|đó|đấy|kia|hôm qua|hom qua|hôm nay"
            + "|hom nay|lúc nãy|luc nay|vừa rồi|vua roi))?(\\s+(của|cua)(\\s+\\p{L}+){1,3})?(\\s+(đã|da|cũng|cung|được"
            + "|duoc)){0,2}";
    /**
     * A-83 (2A): "Xong đơn rồi", "Đơn OK rồi": "xong/ok" right before "đơn", or right after "đơn (hàng) (này) (của X)
     * (đã được)"; not after a condition, not "ổn định", "ok thì", a question ("Đơn ok không?", "Xong đơn chưa?") or
     * "Ok đơn của anh sẽ được duyệt" (review security M4).
     */
    private static final String DONE_WORD_CLAIM = "((?<!(nếu|neu|khi|lúc|luc|để)\\s)(?<!\\p{L})(xong|ok|oke|okay)\\s+(đơn|don)"
            + "(?!\\p{L})" + NOT_AN_ORDER + NOT_DONE + QUESTION + ")"
            + "|((?<!(nếu|neu|khi|lúc|luc|để)\\s)(?<!\\p{L})(đơn|don)(?!\\p{L})" + NOT_AN_ORDER + DONE_WORD_SUBJECT
            + "\\s+(ok|oke|okay|xong|ổn)(?!\\p{L})"
            + "(?!\\s+(định|dinh|không|khong|chưa|chua|thì|thi)(?!\\p{L}))" + QUESTION + ")";
    private static final Pattern ORDER_CLAIM = Pattern.compile(
            "(?iu)((?<!\\p{L})(" + DA + "|được|duoc)\\s+" + ORDER_VERB + "\\s+(đơn|don|order)(?!\\p{L})" + QUESTION + ")"
                    + "|(" + ORDER_SUBJECT + DA + "\\s+((được|duoc)\\s+" + ORDER_AGENT + ")?" + ORDER_VERB + QUESTION + ")"
                    + "|(" + BARE_CLAIM + ")|" + DONE_WORD_CLAIM
                    + "|(" + ORDER_SUBJECT + "(được|duoc)\\s+" + ORDER_AGENT + ORDER_VERB + "\\s+(rồi|roi|xong)(?!\\p{L}))"
                    + "|((?<!(sẽ|nếu|khi|để|cần|thể)\\s)(?<!\\p{L})(đặt|dat|tạo|tao|lên|len)\\s+(hàng|hang|đơn|don)\\s+"
                    + "((đã|da)\\s+)?(thành công|thanh cong)(?!\\p{L}))"
                    // Review security M2: "order's been", "order: confirmed", "has now been", "got approved".
                    + "|((?<!(once|after|when|if|until|before)\\s(your\\s)?)order(\\s+#?\\d+)?(\\s*:|'s|’s|\\s+has"
                    + "|\\s+is|\\s+was|\\s+got)?(\\s+(now|already))?(\\s+been)?(\\s+(now|successfully|already))?"
                    + "\\s+(approved|confirmed|placed"
                    + "|created|processed|shipped|completed)(?!\\p{L}))"
                    + "|((approved|confirmed|placed|processed|shipped)\\s+(your\\s+|the\\s+)?order)");

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
    // A-82 (3A): the same shapes and verbs as ORDER_CLAIM, still without "dat" (A-78), "da" after a word.
    private static final String A_ORDER_VERB = "(duyet|phe duyet|xac nhan|tao|chot|len|ghi nhan|hoan tat|xu ly|xu li|giao"
            + "|thanh toan)(?![a-z])";
    private static final String A_ORDER_AGENT = "((ben em|ben minh|nhan vien|cua hang|shop|chung toi|chung em|he thong"
            + "|quan ly|admin|ke toan|em)\\s+)?";
    private static final String A_NOT_DONE = "(?!(\\s+hang)?(\\s+cua(\\s+[a-z]+){1,2})?\\s+((chua|se|dang)\\s+(duoc|cho|doi|"
            + A_ORDER_VERB + ")|(cho|doi)\\s+(duyet|xu ly|xu li|nhan vien|xac nhan)(?![a-z])))";
    private static final String A_QUESTION = "(?!(\\s+[a-z]+)?\\s*,?\\s*chua(\\s+(a|nhi|vay))?\\s*\\?)";
    private static final String A_NOT_AN_ORDER = "(?!\\s*(gian|vi|gia|le|doc|thuan|dieu|so)(?![a-z]))";
    private static final String A_ORDER_SUBJECT = "(?<!(neu|khi|luc|de)\\s)(?<![a-z])don(?![a-z])"
            + A_NOT_AN_ORDER + A_NOT_DONE + "[^.!?\\n]{0,60}?(?<![a-z])";
    private static final String A_BARE_CLAIM_VERB = "(duyet|phe duyet|xac nhan|tao|chot|len|hoan tat|xu ly|xu li|giao"
            + "|thanh toan)(?![a-z])";
    // A-83 (1A) on the copy: "da" still needs a word before it ("Dạ, xác nhận nhé" is no claim), "duoc" does not.
    private static final String A_DA_AFTER_WORD = "(?<=[a-z][,\\s]|[a-z],\\s)da";
    private static final String A_BARE_TAIL = "(\\s+(?!(bao|thong|lai|yeu|tin|ma|phu|xe|gia|cau|lich|chua|khong|thi|neu"
            + "|khi|sau|de)(?![a-z]))[a-z]+){0,5}\\s*+([^a-z\\d?\\s]|$)";
    private static final String A_BARE_CLAIM = SENTENCE_START
            + "(((da|vang|ok|okay|oke|a)(?![a-z])[\\s,]+|(anh|chi|ban)\\s*,\\s*)){0,3}"
            + A_ORDER_AGENT + "(" + A_DA_AFTER_WORD + "\\s+(duoc\\s+" + A_ORDER_AGENT + ")?|(" + A_DA_AFTER_WORD
            + "\\s+)?duoc\\s+" + A_ORDER_AGENT + ")" + A_BARE_CLAIM_VERB + A_BARE_TAIL;
    // A-83 (2A) on the copy, without "ổn" ("on" is also ôn, ơn).
    private static final String A_DONE_WORD_CLAIM = "((?<!(neu|khi|luc|de)\\s)(?<![a-z])(xong|ok|oke|okay)\\s+don(?![a-z])"
            + A_NOT_AN_ORDER + A_NOT_DONE + A_QUESTION + ")"
            + "|((?<!(neu|khi|luc|de)\\s)(?<![a-z])don(?![a-z])" + A_NOT_AN_ORDER + "(\\s+hang)?(\\s+(nay|do|day|kia"
            + "|hom qua|hom nay|luc nay|vua roi))?(\\s+cua(\\s+[a-z]+){1,3})?(\\s+(da|cung|duoc)){0,2}"
            + "\\s+(ok|oke|okay|xong)(?![a-z])(?!\\s+(khong|chua|thi)(?![a-z]))" + A_QUESTION + ")";
    private static final Pattern STRIPPED_ORDER_CLAIM = Pattern.compile(
            "(?i)(" + A_BARE_CLAIM + ")|" + A_DONE_WORD_CLAIM
                    // A-83 review security L1: "toi da" stays a claim on the copy ("tối đa" and "tôi đã" look the same
                    // there; "Tôi đã duỵêt đơn" must stay blocked), so "Mỗi đơn tối đa giao 10" is an accepted false
                    // positive.
                    + "|(((?<=[a-z] )da|(?<![a-z])duoc)\\s+" + A_ORDER_VERB + "\\s+(don|order)(?![a-z])" + A_QUESTION + ")"
                    + "|(" + A_ORDER_SUBJECT + "da\\s+(duoc\\s+" + A_ORDER_AGENT + ")?" + A_ORDER_VERB + A_QUESTION + ")"
                    + "|(" + A_ORDER_SUBJECT + "duoc\\s+" + A_ORDER_AGENT + A_ORDER_VERB + "\\s+(roi|xong)(?![a-z]))"
                    + "|((?<!(se|neu|khi|de|can|the)\\s)(?<![a-z])(dat|tao|len)\\s+(hang|don)\\s+(da\\s+)?thanh cong"
                    + "(?![a-z]))");
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
