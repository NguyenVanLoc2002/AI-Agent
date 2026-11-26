package com.secureerp.agent.agent.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.secureerp.agent.agent.internal.OutputValidator.Rejection;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * OUTPUT-DIACRITIC-VARIANTS (A-78). A Vietnamese reader still reads "5 triêu" or "đơn hàng đã duỵêt" as an amount or an
 * order claim, but the keyword rules only knew the right spelling and the spelling without diacritics. Every letter
 * here is in the Vietnamese alphabet, so CONFUSABLE_SCRIPT (A-77) does not apply. The validator also checks a copy
 * without diacritics, with keywords chosen so that common words that lose their diacritics the same way are not read
 * as keywords (decision 2A: measured before the change; 3A: a keyword that cannot be told apart is left out and listed
 * in {@link #documentedGapsAreNotBlocked}).
 */
class OutputValidatorDiacriticVariantsTest {

    private static final Set<String> NO_CODES = Set.of();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        // Digits and a misspelled unit.
        "Bộ này 5 triêu                        | MONEY",
        "Bộ này 5 trịeu                        | MONEY",
        "Bộ này 5 tríệu                        | MONEY",
        "Bộ này 1,2 triều thôi                 | MONEY",
        "Bộ này 500 nghín                      | MONEY",
        "Bộ này 500 nghịn                      | MONEY",
        "BỘ NÀY 500 NGHÍN                      | MONEY",
        // Number words and a misspelled unit or number word.
        "Bộ này hai triêu                      | MONEY",
        "Bộ này hái triệu                      | MONEY",
        "Bộ này năm trăm nghín                 | MONEY",
        "Bộ này một trịeu hai                  | MONEY",
        "Bộ này triêu rưỡi                     | MONEY",
        "Bộ này nghín hai                      | MONEY",
        // A misspelled price word followed by spelled numbers or digits; "gía" is matched as written.
        "Gía hai trăm thôi                     | MONEY",
        "Gía 950 thôi                          | MONEY",
        "Gìa hai trăm thôi                     | MONEY",
        "Thành tiến chín trăm                  | MONEY",
        "Thành tiến 900                        | MONEY",
        "Chiêt khấu hai trăm                   | MONEY",
        "Chiêt khấu 10                         | MONEY",
        "Chi phì khoảng hai trăm               | MONEY",
        // Misspelled order claims, both shapes, every verb kept.
        "Đơn hàng đã duỵêt                     | ORDER_CLAIM",
        "Đơn hàng đả duyệt rồi                 | ORDER_CLAIM",
        "Đơn đà được xác nhân                  | ORDER_CLAIM",
        "Đơn hang đã được tạọ                  | ORDER_CLAIM",
        "Đơn đã được phê duyêt                 | ORDER_CLAIM",
        "Đơn hàng đã chột                      | ORDER_CLAIM",
        "Tôi đã duyêt đơn cho anh              | ORDER_CLAIM",
        "Tôi đã phê duyêt đơn cho anh          | ORDER_CLAIM",
        "Em đã xác nhân đơn cho anh            | ORDER_CLAIM",
        "Em đã chôt đơn cho anh                | ORDER_CLAIM",
        "Hệ thống đã được tạọ đơn              | ORDER_CLAIM",
        // Review security M2: two spaces inside a keyword, misspelled or not.
        "Đơn  hàng đã duỵêt                    | ORDER_CLAIM",
        "Đơn  hàng đã duyệt                    | ORDER_CLAIM",
        // Misspelled stock words.
        "Còn lải 5 cái                         | STOCK_NUMBER",
        "Cỏn  lại 5                            | STOCK_NUMBER",
        "Cỏn hàng: 5                           | STOCK_NUMBER",
        "Tồn khọ: 40                           | STOCK_NUMBER",
        "Khọ còn 5                             | STOCK_NUMBER",
        "Khọ hàng 5                            | STOCK_NUMBER",
        "Có 12 cái trong khô                   | STOCK_NUMBER",
        "Có 5 chiếc còn lải                    | STOCK_NUMBER",
        "Có 3 bộ tồn khô                       | STOCK_NUMBER"
    })
    void misspelledKeywordsAreRejected(String text, Rejection expected) {
        assertThat(OutputValidator.check(text.strip(), NO_CODES)).as(text.strip()).contains(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"Bộ này 5 triêu | MONEY", "Đơn hàng đã duỵêt | ORDER_CLAIM"})
    void decomposedMisspellingIsRejected(String text, Rejection expected) {
        String nfd = Normalizer.normalize(text.strip(), Normalizer.Form.NFD);
        assertThat(OutputValidator.check(nfd, NO_CODES)).contains(expected);
    }

    /**
     * Ordinary answers whose words lose their diacritics into a keyword: "dòng", "động", "đông", "đóng" → "dong";
     * "tốn", "tôn" → "ton"; "côn", "cồn" → "con"; "trạm" → "tram"; "ngăn", "ngắn" → "ngan"; "giả", "tông", "giám",
     * "phí" → price words; "từ", "làm" → number words; "dạ", "đạt" → "da", "dat"; "khó", "khô" → "kho".
     */
    @ParameterizedTest
    @ValueSource(strings = {
        // dong
        "Xe có 2 động cơ điện", "Mẫu này có 2 dòng, bạn chọn dòng nào?", "Bạn chọn 1 trong 2 dòng xe nhé",
        "Đông lạnh 2 lần thì hỏng", "Đã đóng 2 thùng hàng", "Mỗi xe có hai dòng điện",
        "Có 2 đồng hồ trên táp lô", "Đồng bộ 2 chi tiết",
        // ton, con, kho
        "Việc này tốn 2 ngày", "Thay cái này tốn 1 giờ", "Mái tôn 2 lớp", "Lá côn 200mm phù hợp xe này",
        "Bộ côn 3 chi tiết", "Lau bằng cồn 90 độ", "Hơi khó 2 chỗ này", "Phần này khô 2 giờ là được",
        // tram, ngan, van, canh
        "Xe cách 2 trạm sạc", "Ba trạm dừng", "Hộp có 2 ngăn", "Có 2 ngắn và 2 dài", "Động cơ 16 van",
        "Siết 2 vặn là chặt", "Hộp có 6 cạnh",
        // price words and number words
        "Giả sử bạn cần 2 bộ", "Tông màu 2 lớp", "Tông màu hai ba lớp", "Giám sát hai ba cảm biến",
        "Giảm xóc loại này lắp từ hai bên", "Phí lắp đặt làm từ ba đến bốn tiếng", "Chi phí lắp từ hai đến ba tiếng",
        "Gian hàng có hai ba mẫu", "Hãng Delphi có hai ba mẫu", "Giá trị hai ba mẫu khác nhau",
        "Tổng cộng hai bộ", "Tổng cộng hai ba bộ", "Chi phí tính theo hai ba bộ",
        "Giá năm nay ổn định", "Thành tiền tạm tính sau khi chốt",
        // order words
        "Dạ chốt đơn ở nút bên dưới nhé", "Dạ, xác nhận đơn bằng nút bên dưới", "Bạn đã đạt đơn tối thiểu",
        "Đơn đã đạt mức tối thiểu", "Ghế da được tạo kiểu đẹp", "Bạn đã đạt 2 tiêu chuẩn", "Tôi đã dọn đơn giản lại",
        "Bạn đặt đơn ở nút bên dưới nhé", "Đơn của bạn đang chờ nhân viên xác nhận",
        "Đơn giản thôi, bạn gửi giúp số VIN",
        // units next to a measure, and plain answers
        "Lốp này đi 2 vạn km", "Xe chạy 5 nghìn km thì thay", "Triều đình nhà Nguyễn", "Bạn mua 2 cái cũ hay mới?",
        "Xe máy 2 thì dùng loại này", "Động cơ 2 kỳ dùng loại này", "Xe 2 cầu dùng loại khác",
        "Xe tải 2 tấn dùng loại này", "Đổi 2 vòng bi", "Gioăng 2 lớp", "Năm 2019 xe dùng loại này",
        "Bạn chờ tôi 2 phút", "Có 2 trục cam", "Bạn cần 2 bộ má phanh phải không?", "Xe 4 chỗ dùng loại này",
        "Còn hàng, bạn muốn đặt mấy bộ?", "Tháng 2 có hàng mới", "Đèn 2 tầng", "Phanh đĩa 2 piston", "Hai tấm che",
        "Con đường này dài 2 cây số", "Hộp số 6 cấp", "Bạn kiểm tra giúp mã OE nhé",
        // Were blocked by the older price-word rule until A-79 (OUTPUT-PRICE-WORD-BOUNDARY).
        "Hệ thống giám sát 2 cảm biến", "Thời gian bảo hành 12 tháng"
    })
    void ordinaryAnswersPass(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    /**
     * 3A, documented gaps: misspellings whose form without diacritics is also a common word, so the copy without
     * diacritics does not check them (đồng, trăm, ngàn, tỷ, vạn, cành, củ; tồn/kho/còn alone; giá/tổng/giảm except
     * "gía"; the verb đặt; "Đã …" with no word before it). Changing one of these is an owner decision, not a test fix.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "Bộ này 50 đông", "Bộ này 5 trằm", "Bộ này 500 ngăn", "Bộ này 2 tý", "Bộ này 12 vặn", "Bộ này 500 cạnh",
        "Bộ này 1 cũ 2", "Tôn: 40", "Giấ 950", "Tông hai trăm", "Em đã dặt đơn cho anh", "Đả duyêt đơn cho anh"
    })
    void documentedGapsAreNotBlocked(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    /** Review QA: the copy without diacritics stays fast on the longest allowed answer. */
    @ParameterizedTest
    @ValueSource(strings = {"chi phí ", "thành tiền ", "hai ", "đơn hàng ", "triêu "})
    void longAnswersStayFast(String start) {
        String text = (start + "hai mươi ba ".repeat(200)).substring(0, OutputValidator.MAX_CHARS);
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> OutputValidator.check(text, NO_CODES));
    }
}
