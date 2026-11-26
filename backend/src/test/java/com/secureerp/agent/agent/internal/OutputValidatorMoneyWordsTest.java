package com.secureerp.agent.agent.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.secureerp.agent.agent.internal.OutputValidator.Rejection;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * OUTPUT-MONEY-WORDS (A-76): amounts the model may invent in words or slang. The model never receives a price (type
 * gate), so these are made-up prices, still never shown to a customer. Blocking is biased to fail closed (2A): a
 * blocked answer is handed to staff.
 */
class OutputValidatorMoneyWordsTest {

    private static final Set<String> NO_CODES = Set.of();

    @ParameterizedTest
    @ValueSource(strings = {
        // Spelled numbers with a money unit.
        "Má phanh giá một triệu hai trăm nghìn",
        "Bộ này một triệu hai trăm nghìn thôi",
        "Bộ này hai triệu đồng",
        "Chỉ năm trăm nghìn một bộ",
        "Bộ này một trăm hai mươi lăm nghìn đồng",
        "Bộ này mười hai triệu",
        "Bộ này một tỷ",
        "Bộ này ba trăm ngàn",
        "Bộ này triệu rưỡi",
        "Khoảng hai triệu tư",
        "BỘ NÀY HAI TRIỆU",
        "Hai TRIỆU một bộ",
        "Bộ này hai mươi k",
        "Bộ này hai tr",
        // Without diacritics.
        "Bo nay mot trieu hai",
        "Bo nay hai tram nghin",
        "Bo nay nam tram ngan",
        "Bo nay mot ty",
        "Bo nay nghin hai",
        // Units missing from the digit rule.
        "Bộ này 2 tỷ",
        "Bộ này 2 tỉ",
        "Bộ này 2tỷ",
        "Bộ này 12 vạn",
        // Slang.
        "Bộ này 1tr25 thôi",
        "Bộ này 1tr5 thôi",
        "Bộ này 1tr. thôi",
        "Bộ này 1 củ 2 thôi",
        "Bộ này 2 củ thôi",
        "Bộ này 2 củ đều được",
        "Bộ này hai củ thôi",
        "Bộ này 1m2 thôi",
        "Bộ này 500 cành",
        "Bộ này 5 trăm",
        // English.
        "Bộ này 1.2 million",
        "Bộ này two million dong",
        "Bộ này 500 thousand",
        // A price word followed by spelled numbers in the same sentence.
        "Giá hai trăm thôi",
        "Tổng là ba trăm năm mươi",
        "Thành tiền chín trăm",
        "Giá một bộ là hai trăm rưởi",
        "Giá mỗi bộ: hai trăm rưởi",
        "Giá bên em hai trăm",
        "Giảm hai trăm cho anh",
        "Chi phí khoảng hai trăm",
        // Separators other than a plain space (review security M1).
        "Bộ này hai-triệu",
        "Bộ này 2-triệu",
        "Khoảng (hai) triệu",
        "Bộ này hai triệu",
        "Bộ này hai triệu",
        "Bộ này hai  triệu"
    })
    void amountsInWordsOrSlangAreRejected(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).contains(Rejection.MONEY);
    }

    @Test
    void decomposedDiacriticsAreReadLikeComposedOnes() {
        String decomposed = Normalizer.normalize("Bộ này hai triệu", Normalizer.Form.NFD);
        assertThat(OutputValidator.check(decomposed, NO_CODES)).contains(Rejection.MONEY);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Bạn cần 2 bộ má phanh phải không?",
        "Xe Vios 2019 dùng 4 lít dầu",
        "Mua 1 củ đề cho xe",
        "Xe cần thay hai củ phát",
        "Hai bộ má phanh và một lọc dầu",
        "Bạn đợi một chút nhé",
        "Xe cần thay 2 van xả",
        "Năm nay xe dùng ba bộ lọc gió",
        "Một hai ngày nữa sẽ có hàng",
        "Bạn cần mấy cái, một hay hai?",
        "Giá trị bảo hành theo hãng",
        "Ba bộ, mỗi bộ hai cái",
        // Unit words as the start of another word ("của", "tỉnh", "vạch", "cảnh").
        "Xe 2019 của bạn dùng loại nào?",
        "Hàng có ở 2 tỉnh phía nam",
        "Có 3 vạch báo trên đồng hồ",
        "Hai cảnh báo trên đồng hồ",
        // A price word before a count, a time, or a single number word ("bao" starts like "ba").
        "Tổng cộng hai bộ má phanh",
        "Giá bao nhiêu thì nhân viên sẽ báo anh",
        "Giá năm nay tăng",
        "Tổng kết năm",
        "Tổng cộng hai ba bộ",
        "Giá có thể đổi trong hai ba bốn ngày",
        "Giá nhân viên sẽ báo. Bốn năm xe đã dùng loại này",
        // Mileage, speed and parts words (reviews QA M1/M3, security M5/M6).
        "Thay dầu mỗi năm nghìn km",
        "Thay dầu mỗi mười nghìn km",
        "Xe đã chạy hai trăm nghìn km",
        "Bảo hành ba năm hoặc một trăm nghìn km",
        "Bảo hành 3 năm hoặc 100 nghìn km",
        "Động cơ không tải khoảng một nghìn vòng",
        "Khớp vạn năng",
        "Cờ lê 12 vạn năng",
        "Tỷ lệ nén 10:1",
        "Pha nước làm mát theo một tỉ lệ phù hợp",
        "Xe dùng 2 trống phanh sau",
        "Xe có 2 trục",
        "Thay 2 trên, 2 dưới",
        "Động cơ 4 kỳ",
        "Cần 2 kẹp phanh",
        "Đèn báo nằm phía sau đồng hồ",
        "Kiểm tra hai đồng hồ",
        "Xe có 2 đồng hồ",
        "Hai củ phát sinh lỗi thì báo em"
    })
    void ordinaryAnswersStillPass(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    /**
     * 2A, accepted false positives: blocked and handed to staff. "1m2" is also a length (decision 1A). "Giao tới 2
     * tỉnh" passes since A-79 (price words are whole words, {@link OutputValidatorPriceWordTest}).
     */
    @ParameterizedTest
    @ValueSource(strings = {"Một triệu cảm ơn bạn", "Dây dài 1m2"})
    void acceptedFalsePositivesAreBlocked(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).contains(Rejection.MONEY);
    }

    /**
     * 3A, documented gaps (A-76): not blocked. Changing one of these is an owner decision, not a test fix. Spelled
     * numbers without a unit or price word, a bare short number and rare slang. Look-alike letters ("450к") are now
     * rejected by OUTPUT-CONFUSABLES (A-77, {@link OutputValidatorConfusablesTest}). The first barrier is that the
     * model never receives a price.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "Ba trăm rưởi một cái", "Bộ này 1250 thôi", "Hai trăm nhé anh", "Bộ này 1t2", "Bộ này 1 ng thôi",
        "Bộ này 1 xị"
    })
    void documentedGapsAreNotBlocked(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    /** Review security: the rules stay fast on the longest allowed answer. */
    @ParameterizedTest
    @ValueSource(strings = {"1 ", "ba ", "giá là ", "hai - ", "1-", "triệu "})
    void longRepetitiveAnswersAreCheckedQuickly(String unit) {
        String text = unit.repeat(OutputValidator.MAX_CHARS).substring(0, OutputValidator.MAX_CHARS - 1) + "x";
        assertTimeoutPreemptively(Duration.ofMillis(500), () -> OutputValidator.check(text, NO_CODES));
    }
}
