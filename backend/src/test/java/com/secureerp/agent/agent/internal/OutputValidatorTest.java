package com.secureerp.agent.agent.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.agent.internal.OutputValidator.Rejection;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** T-GW-OUTPUT and T-UC02-OE-FABRICATED (AI_GATEWAY §4): model text a customer must not see. */
class OutputValidatorTest {

    private static final Set<String> TOOL_CODES = Set.of("SKU-BRK-PAD-A", "04465-0K290");

    @ParameterizedTest
    @ValueSource(strings = {
        "Chúng tôi có má phanh trước mã 04465-0K290 (SKU-BRK-PAD-A), tình trạng: còn hàng.",
        "Bạn cần 2 bộ má phanh phải không? Tôi sẽ tạo báo giá cho bạn.",
        "Xin chào! Bạn vui lòng cho biết mã OE hoặc số VIN của xe.",
        "Báo giá đã được gửi bên dưới, bạn kiểm tra giúp nhé.",
        "Xe Vios 2019 của bạn dùng loại lọc dầu nào?"
    })
    void safeAnswersPass(String text) {
        assertThat(OutputValidator.check(text, TOOL_CODES)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Giá má phanh là 555.444.333 đ                                    | MONEY",
        "Tổng cộng 1.250.000 VND                                          | MONEY",
        "Giá chỉ 450k thôi                                                | MONEY",
        "Price: $25                                                        | MONEY",
        "Đơn giá khoảng 2 triệu                                            | MONEY",
        "Kho còn 12 cái                                                    | STOCK_NUMBER",
        "Tồn kho: 40                                                       | STOCK_NUMBER",
        "Mã đúng là 90915-YZZD9                                            | UNKNOWN_PART_CODE",
        "Bạn dùng mã 17801-0Y040 nhé                                       | UNKNOWN_PART_CODE",
        "Xem thêm tại https://example.test/phanh                           | LINK",
        "Truy cập www.example.test                                         | LINK",
        "Đặt hàng tại shop-phutung.vn                                      | LINK",
        "[bấm vào đây](javascript:alert(1))                                | LINK",
        "Mã báo giá của bạn: qh_AAAAAAAAAAAAAAAAAAAAAA                     | SERVER_TOKEN",
        "Sản phẩm pr_BBBBBBBBBBBBBBBBBBBBBB                                | SERVER_TOKEN",
        "Đơn hàng đã được duyệt, bạn yên tâm                               | ORDER_CLAIM",
        "Tôi đã duyệt đơn cho bạn                                          | ORDER_CLAIM",
        "Your order has been approved                                      | ORDER_CLAIM",
        "Mã khách 9876543210                                               | LONG_NUMBER"
    })
    void forbiddenContentIsRejected(String text, Rejection expected) {
        assertThat(OutputValidator.check(text.strip(), TOOL_CODES)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void emptyTextIsRejected(String text) {
        assertThat(OutputValidator.check(text, TOOL_CODES)).contains(Rejection.EMPTY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Xin chao\u0007", "Xin‮chao"})
    void controlAndFormatCharactersAreRejected(String text) {
        assertThat(OutputValidator.check(text, TOOL_CODES)).contains(Rejection.CONTROL_CHARACTERS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"５５５４４４３３３ đ", "giá: ５５５"})
    void fullWidthDigitsAreNormalizedFirst(String text) {
        assertThat(OutputValidator.check(text, TOOL_CODES)).contains(Rejection.MONEY);
    }

    @ParameterizedTest
    @ValueSource(ints = {2000, 2001})
    void lengthLimit(int length) {
        Optional<Rejection> result = OutputValidator.check("a".repeat(length), TOOL_CODES);
        if (length > OutputValidator.MAX_CHARS) {
            assertThat(result).contains(Rejection.TOO_LONG);
        } else {
            assertThat(result).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"04465-0K290", "SKU-BRK-PAD-A"})
    void aCodeIsAllowedOnlyWhenAToolReturnedItThisTurn(String code) {
        assertThat(OutputValidator.check("Mã " + code, TOOL_CODES)).isEmpty();
        assertThat(OutputValidator.check("Mã " + code, Set.of())).contains(Rejection.UNKNOWN_PART_CODE);
    }
}
