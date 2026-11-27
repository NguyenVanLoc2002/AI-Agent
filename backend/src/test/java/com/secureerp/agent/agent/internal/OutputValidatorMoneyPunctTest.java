package com.secureerp.agent.agent.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.secureerp.agent.agent.internal.OutputValidator.Rejection;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * OUTPUT-MONEY-PUNCT (A-89). (1A) Punctuation between a number and its money unit ("**45** USD", "45 (USD)"); (2A)
 * punctuation between a currency code and its number ("SGD - 45", "USD (45)"); (3A) a code before number words ("USD
 * fifty"); (4A) more currency codes and names ("45 CHF", "45 peso"). Counts, sizes and ranges must still pass.
 */
class OutputValidatorMoneyPunctTest {

    private static final Set<String> NO_CODES = Set.of();

    @ParameterizedTest
    @ValueSource(strings = {
        // 1A: punctuation after the number.
        "**45** đô la", "**45** USD", "**45** triệu", "**45**k", "**45 USD**", "45 (SGD)", "45 (USD)", "45 [USD]",
        "45 – yuan", "45 — USD", "45/tệ", "45 \"baht\"", "45 'USD'", "45 “USD”", "45* USD", "45 ~ USD", "45 | USD",
        "45 ≈ USD", "45 = USD", "45 : USD", "45 (đ)", "45 (nghìn)", "45 ** (USD)", "45/k", "45 *(* triệu",
        "45 – đồng", "**1.200.000** ₫", "45 (€)",
        // 2A: punctuation after the code.
        "SGD - 45", "SGD (45)", "Rs=45", "USD (45)", "$ (45)", "USD **45**", "USD – 45", "€ = 45", "VND ~ 45",
        "USD: (45)", "**USD** 45", "CHF (45)",
        // 3A: a code before number words.
        "SGD forty-five", "CNY fifty", "USD fifty", "$ fifty", "USD: twenty", "USD (fifty)", "USD hai mươi",
        "VND năm trăm", "dollars twenty",
        // 4A: more codes and names after a number.
        "45 CHF", "45 NZD", "45 MYR", "45 TWD", "45 RUB", "45 AED", "45 IDR", "45 KHR", "45 LAK", "45 peso", "45 pesos",
        "45 franc", "45 francs", "45 dirham", "45 dirhams", "45 rupiah", "45 riel", "45 chf",
        // 4A: the new codes before a number.
        "CHF 45", "NZD 45", "MYR 45", "TWD 45", "AED 45", "IDR 4500", "KHR 45", "LAK 45",
        // Review security M1: a hyphen, comma or dot after a mark.
        "**45** - USD", "(45) - USD", "**45**-USD", "45 (-USD)", "**45**, USD", "**45**. USD",
        // Review QA H1: the same kind of brackets, quotes and dashes.
        "`45` USD", "45 `USD`", "45 <USD>", "45 {USD}", "45 « USD »", "45 ‹USD›", "45 − USD", "45 ‐ USD", "45 ‑ USD",
        "USD `45`", "USD <45>", "USD − 45", "USD ‐ 45",
        // Review security M2, QA H2: punctuation after number words.
        "**fifty** dollars", "**năm mươi** đô", "fifty / USD", "fifty **USD**", "năm *triệu*", "fifty: USD",
        "hai mươi / nghìn", "fifty [USD]", "hai [nghìn]", "fifty `USD`",
        // Review QA L6, L7: codes from A-88 with punctuation, combined marks.
        "45 Rs.", "45 rmb", "45 cny", "45 hkd", "45 krw", "45 inr", "45 thb", "45 cad", "45 aud", "RMB 45", "AUD 45",
        "INR 45", "45 … USD", "USD ( ( 45", "$ **fifty**", "USD – hai mươi lăm", "(45) [USD]", "2 rub",
        "45 (tr)", "45 (k)", "**45** k"
    })
    void amountsAreRejected(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).contains(Rejection.MONEY);
    }

    /** Counts, sizes, ranges and formatting that are not amounts. */
    @ParameterizedTest
    @ValueSource(strings = {
        "2 (bộ)", "2 (cái)", "**2** bộ", "2 – 3 ngày", "2 (hai) bộ", "Bảng (2) thông số", "M8 x 1.25", "2 = 2 cái",
        "2 x 4 bộ", "2 ~ 3 bộ", "loại (2)", "**Lưu ý**: 2 bộ", "2 / tháng", "PHP 8 code", "45 km (khoảng)",
        "2 cái (USB)", "2 cổng (USB-C)", "kiểu 2 (Pháp)", "2 chiếc peso", "Bulông 2 / M8", "2 / M10", "2 (m)",
        "Rub 2 times", "rub 2 coats", "USD sau khi đổi", "USD ba", "Trả bằng USD một lần",
        "2 x USD",
        // Review security L3–L7: car models, page numbers, "đồng ý", "dong co".
        "Kia K3 / K5", "Hyundai i10 (Grand i10)", "i10 / Grand i10", "xem mục 3 (tr. 12)", "Động cơ 2.0 (Euro IV)",
        "Euro 2 (Euro II)", "Buoc 2: dong co", "Bước 2: Đồng ý", "1) Đồng ý", "2 đồng ý",
        // Review QA M3: steps and lists.
        "Bước 2: đồng bộ", "Mục 3: đồng hồ", "2) Tỷ lệ nén", "2 (km/h)", "Bước 2: Kiểm tra", "Bảng 2: thông số",
        "Bảo hành hai năm. Triệu chứng khác", "USD hai bộ", "USD một", "2 (tr. 5)"
    })
    void ordinaryTextPasses(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    /**
     * Documented gaps (A-89): more than four punctuation marks; "x" between a number and a unit; "PHP", "kip", "RUB"
     * before a number (4A); one Vietnamese number word after a code (3A); the marks ";", "+", "•", "·", "#"
     * (review QA H1); "," after a code ("USD, 45"); "a hundred"; amounts without a currency ("45/bộ", A-88 3A).
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "45 ***** USD", "USD ***** 45", "45 x USD", "45 PHP", "45 kip", "RUB 45", "USD hai", "45; USD", "45 + USD",
        "45 • USD", "45 · USD", "45 # USD", "45 ** (**USD**)", "USD; 45", "USD, 45", "USD a hundred", "45/bộ"
    })
    void documentedGapsAreNotBlocked(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    /** Accepted false positives (A-89): blocked and handed to staff. */
    @ParameterizedTest
    @ValueSource(strings = {
        "Chương 2 – USD là đơn vị", "2 (k)", "USD one time", "Loại 2 / K", "USD ten times", "$ one", "AUD two",
        "(2) củ sạc", "2: Đô", "tỷ giá USD ba năm qua", "Dak Lak 2 chi nhánh"
    })
    void acceptedFalsePositivesAreBlocked(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).contains(Rejection.MONEY);
    }

    /** The new alternatives stay fast on the longest allowed answer. */
    @ParameterizedTest
    @ValueSource(strings = {"1 (", "1 *", "1 * ", "USD (", "USD - ", "$ (", "1 ( ( ( ( ", "USD hai ", "1/", "**1** ",
        "USD * ", "fifty (", "1 *-", "1 (-, ", "hai *", "fifty ** "})
    void longAnswersStayFast(String piece) {
        String text = piece.repeat(OutputValidator.MAX_CHARS / piece.length());
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> OutputValidator.check(text, NO_CODES));
    }
}
