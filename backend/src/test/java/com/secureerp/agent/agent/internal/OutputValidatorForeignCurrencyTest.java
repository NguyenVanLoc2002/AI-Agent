package com.secureerp.agent.agent.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.secureerp.agent.agent.internal.OutputValidator.Rejection;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * OUTPUT-FOREIGN-CURRENCY (A-87). (1A) Vietnamese names of foreign money after a number, in digits or in words ("45
 * đô", "năm mươi đô", "45 tệ", "45 bảng Anh"), and rúp, ringgit, rupee, yuan, baht; (2A) currency codes before or
 * after a number ("45 SGD", "SGD 45", "Rs 45"). Words that start the same way ("đô thị", "tệ nạn", "yên xe", "bảng
 * mạch", "xu hướng") must still pass.
 */
class OutputValidatorForeignCurrencyTest {

    private static final Set<String> NO_CODES = Set.of();

    @ParameterizedTest
    @ValueSource(strings = {
        // 1A: "đô" forms.
        "45 đô", "45 đô la", "45 đôla", "45 đô-la", "45 đô - la", "45 đô Mỹ", "45 Đô", "45 ĐÔ LA", "45.000 đô",
        "khoảng 45 đô một bộ", "Phụ tùng này 45 đô.", "45đô", "45 do la", "45 dola", "45 do-la",
        // 1A: number words.
        "năm mươi đô", "hai trăm đô", "ba mươi lăm đô la", "hai mươi tệ", "mười bảng Anh", "fifty yuan",
        // 1A: other names.
        "45 tệ", "45 nhân dân tệ", "45 nhan dan te", "45 bảng Anh", "45 bang Anh", "45 BẢNG ANH", "45 yên Nhật",
        "45 yen nhat", "45 rúp", "45 rup", "45 ringgit", "45 rupee", "45 rupees", "45 yuan", "45 baht", "45 Baht",
        // Review security M1: hyphens, brackets, runs of spaces and line breaks between the words.
        "45 nhân  dân tệ", "45 nhân-dân-tệ", "45 nhân\ndân tệ", "45 bảng-Anh", "45 bảng (Anh)", "45 yên-Nhật",
        // Review security L5: other currency symbols.
        "45₽", "₽45", "฿45", "45 ฿", "₱45", "45₺", "₪45",
        // Review QA H: decomposed, full-width, no-break space, a digit run with a space.
        "45 đo\u0302", "45 đo\u0302 la", "４５ ＳＧＤ", "45\u00A0đô", "4 5 đô", "45 Đô la",
        // Review QA M: number words with the new names and codes.
        "fifty rupees", "ten baht", "ba tệ", "một trăm nhân dân tệ", "twenty AUD", "tư đô",
        // 2A: codes after a number.
        "45 AUD", "45 SGD", "45 CNY", "45 RMB", "45 THB", "45 KRW", "45 HKD", "45 CAD", "45 INR", "45 quid", "45 Rs",
        "45 sgd", "45,5 AUD", "45sgd",
        // 2A: codes before a number.
        "SGD 45", "CNY: 45", "Rs 45", "Rs. 45", "Rs.45", "INR 4500", "quid 45"
    })
    void foreignAmountsAreRejected(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).contains(Rejection.MONEY);
    }

    /** A code glued to the digits reads as a part code and is rejected before the money rules. */
    @ParameterizedTest
    @ValueSource(strings = {"45AUD", "AUD45", "45SGD"})
    void gluedCodesAreRejected(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).contains(Rejection.UNKNOWN_PART_CODE);
    }

    /** Review QA H: an invisible character inside a name is rejected (CONTROL or CONFUSABLE runs first). */
    @ParameterizedTest
    @ValueSource(strings = {"45 đ\u200Bô", "45 t\u200Bệ", "45 S\u200BGD"})
    void hiddenCharactersAreRejected(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isPresent();
    }

    /** Words that start like a currency name, and counts. */
    @ParameterizedTest
    @ValueSource(strings = {
        "2 yên xe", "yên xe 2 chiếc", "2 bảng mạch", "bảng 2 cột", "bảng giá ở dưới", "Bảng 2: thông số", "2 xu hướng",
        "xu páp 2 cái", "2 đô thị", "thủ đô 2", "45 độ", "nhiệt độ 45 độ C", "2 đôi giày", "45 đồ nghề",
        "We won 2 awards", "2 tệp", "2 tệ nạn", "2 tệ hại", "2 te", "chạy 45 km", "45 cái", "2 cắc kè",
        "bảng 45 x 30 cm", "2 đô vật", "hai đô thị", "Audi RS 6", "RS 3 Sportback", "RS6", "2 bạt phủ xe",
        "Loại 2 đó là loại tốt", "2 bảng", "2 tệp đính kèm", "Cadillac 2 cửa", "2 audio", "2 đôi", "45 độ C",
        "Cadillac", "2 Rsx", "Rs", "SGD", "SGD.", "quid pro quo 2",
        // Review security M3, L4: "anh" addresses the customer; "tệ" also means bad.
        "Em gửi 2 bảng anh tham khảo nhé", "Loại 2 tệ hơn loại 1", "Hàng 2 tệ quá", "loại 3 tệ nhất"
    })
    void ordinaryTextPasses(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    /**
     * Documented gaps (A-87): "yên", "bảng", "xu", "cắc", "bạt", "won" alone (1A); amounts without a currency, often
     * quantities (3A); "do la" on the stripped copy, as "2 đó là" would read the same; "bảng anh" with a lower-case a
     * (review security M3); a code before number words; punctuation between the number and the unit (all units, also
     * before A-87: review security M2); currencies not in the approved list (review QA M); typos ("đôlar", "nhân dân
     * tê").
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "45 yên", "45 bảng", "45 xu", "45 cắc", "45 bạt", "45 won", "45 each", "45 per unit", "45 apiece", "45 một bộ",
        "45/bộ", "45 mỗi cái", "45 dô la", "RS 45", "45 bang anh", "SGD forty-five", "CNY fifty", "**45** đô la",
        "45 (SGD)", "45 – yuan", "SGD - 45", "Rs=45", "45 CHF", "45 NZD", "45 MYR", "45 TWD", "45 RUB", "45 AED",
        "45 peso", "45 franc", "45 dirham", "45 đôlar", "45 nhân dân tê"
    })
    void documentedGapsAreNotBlocked(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    /** Accepted false positives (A-87): blocked and handed to staff. */
    @ParameterizedTest
    @ValueSource(strings = {"2 CAD files", "3 cad models", "bản vẽ CAD 2D", "nam do la nam tot", "Loai 2 do la loai tot"})
    void acceptedFalsePositivesAreBlocked(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).contains(Rejection.MONEY);
    }

    /** The new alternatives stay fast on the longest allowed answer. */
    @ParameterizedTest
    @ValueSource(strings = {"1 đô ", "1 đô-", "1 do ", "Rs ", "1 nhân dân ", "1 bảng ", "hai đô ", "1 yên ", "SGD ", "45 SGD ",
        "1 nhân dân tệ ", "1 do la ", "đô ", "1 nhân - ", "₽"})
    void longAnswersStayFast(String piece) {
        String text = piece.repeat(OutputValidator.MAX_CHARS / piece.length());
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> OutputValidator.check(text, NO_CODES));
    }

    /** Review security L8: one long run of spaces after a name. */
    @ParameterizedTest
    @ValueSource(strings = {"1 đô", "1 do", "1 nhân", "1 bảng"})
    void longSpaceRunStaysFast(String start) {
        String text = start + " ".repeat(OutputValidator.MAX_CHARS - start.length());
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> OutputValidator.check(text, NO_CODES));
    }
}
