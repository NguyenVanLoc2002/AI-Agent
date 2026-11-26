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
 * OUTPUT-ORDER-CLAIM-REST (A-83). The order claims that still passed after A-82: (1A) no "đơn" at all ("Đã được duyệt
 * rồi ạ"), (2A) "xong/ok" as the verb ("Xong đơn rồi", "Đơn OK rồi"), (3A) up to 60 characters between "đơn" and "đã"
 * (window cases in {@code OutputValidatorOrderClaimTest}), (4A) "đã" misspelled with its "đ" ("Đả duyệt đơn"). The rule
 * with diacritics and the copy without diacritics overlap, so some groups below are seen by one layer only.
 */
class OutputValidatorOrderClaimRestTest {

    private static final Set<String> NO_CODES = Set.of();

    @ParameterizedTest
    @ValueSource(strings = {
        // 1A, seen by both layers.
        "Đã được duyệt rồi ạ",
        "Dạ, đã được duyệt rồi ạ",
        "Được duyệt rồi ạ",
        "Đã được nhân viên duyệt rồi",
        "Cảm ơn anh. Đã được duyệt rồi ạ",
        "Bên em đã chốt rồi nhé",
        "Em đã xác nhận rồi ạ",
        "Vâng, em đã giao rồi",
        // 1A, with diacritics only ("da" first in the sentence is "Dạ" on the copy).
        "Đã duyệt rồi ạ",
        "Đã thanh toán xong.",
        "Đã giao",
        "Đã đặt thành công",
        "Đã xác nhận, anh chờ giao hàng nhé",
        "Dạ vâng, đã duyệt rồi ạ",
        // 1A, review QA H1 and security H1: other endings, words after the verb, an emoji or a symbol after it.
        "Đã được duyệt rồi anh nhé",
        "Em đã xác nhận rồi chị ạ",
        "Đã duyệt rồi nha",
        "Đã duyệt rồi đó anh",
        "Đã giao rồi anh ạ",
        "Bên em đã duyệt cho anh rồi ạ",
        "Đã chốt giúp anh rồi",
        "Đã duyệt luôn rồi",
        "Đã được duyệt hết rồi ạ",
        "Đã được duyệt rồi ạ 😊",
        "Đã được duyệt rồi ạ 🎉",
        "Đã duyệt rồi ạ - cảm ơn anh",
        "Đã duyệt rồi ạ :)",
        "Đã duyệt rồi: anh chờ giao hàng",
        // 1A, review QA H2 and security H2: markdown, a bullet, a number, an emoji, a colon, a greeting first.
        "**Đã duyệt rồi ạ**",
        "- Đã giao rồi",
        "1. Đã duyệt rồi",
        "> Đã duyệt rồi",
        "✅ Đã được duyệt rồi ạ",
        "Thông báo: đã được duyệt rồi",
        "Trạng thái: đã được duyệt.",
        "Cảm ơn anh.\rĐã được duyệt rồi",
        "Dạ anh, đã được duyệt rồi ạ",
        "Dạ vâng ạ, đã được duyệt rồi",
        // 1A, review security M2: who did it.
        "Hệ thống đã duyệt rồi ạ",
        "Quản lý đã chốt rồi",
        "Admin đã xác nhận rồi",
        // 1A, upper case, a tab and double spaces, "đa" for "đã".
        "ĐÃ ĐƯỢC DUYỆT RỒI Ạ",
        "Đã\tđược  duyệt rồi",
        "Đa duyệt rồi",
        // 1A, misspelled verb: the copy only.
        "Dạ, đã được duỵêt rồi ạ",
        "Đã được duỵêt rồi",
        "Em đã xác nhân rồi ạ",
        "da duoc duyet roi a",
        "DA DUOC DUYET ROI A",
        "- da duoc duyet roi anh nhe",
        "Da, da duoc duyet roi a",
        "em da xac nhan roi",
        // 2A, both layers.
        "Xong đơn rồi",
        "Đơn OK rồi",
        "Đơn ok",
        "Đơn của anh ok rồi",
        "Đơn hàng đã xong rồi ạ",
        "Ok đơn rồi nhé",
        "Đơn cũng xong rồi",
        "Đơn của anh xong rồi",
        // 2A, review security M1: "này/đó/hôm qua", more words after "của", "đã được ok".
        "Đơn này ok rồi",
        "Đơn đó xong rồi ạ",
        "Đơn hôm qua ok rồi",
        "Đơn của anh bên em ok rồi",
        "Đơn đã được ok rồi",
        "don nay ok roi",
        "xong don roi",
        "don ok roi",
        // 2A, with diacritics only ("on" is not read as "ổn" on the copy).
        "Đơn ổn rồi anh",
        // 2A, misspelled "đơn": the copy only.
        "Xong đon rồi",
        "Đon OK rồi",
        // 3A: 50 characters between "đơn" and "đã" (A-82 review security M3); the 59/60/61 boundary is in
        // OutputValidatorOrderClaimTest.
        "Đơn hàng anh gửi bên em hôm qua lúc chiều, em báo là đã được duyệt",
        // 4A: "đã" misspelled with its "đ", with diacritics only.
        "Đả duyệt đơn rồi",
        "Đá duyệt đơn",
        "Đạ xác nhận đơn cho anh",
        "Đơn đả được duyệt",
        "Đơn hàng đà được xác nhận",
        "Đả được duyệt rồi"
    })
    void remainingClaimsAreRejected(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).contains(Rejection.ORDER_CLAIM);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Đã được duyệt rồi ạ", "Đơn ổn rồi anh", "Đả duyệt đơn rồi", "Xong đơn rồi", "Đơn của anh ok rồi",
        "Đã được duyệt rồi anh nhé"
    })
    void decomposedClaimIsRejected(String text) {
        String nfd = Normalizer.normalize(text, Normalizer.Form.NFD);
        assertThat(OutputValidator.check(nfd, NO_CODES)).as(text).contains(Rejection.ORDER_CLAIM);
    }

    /** A sentence with some characters composed and some decomposed (QA review M2). */
    @Test
    void mixedNormalizationClaimIsRejected() {
        String text = "Dạ, " + Normalizer.normalize("đã được duyệt", Normalizer.Form.NFD) + " rồi ạ";
        assertThat(OutputValidator.check(text, NO_CODES)).contains(Rejection.ORDER_CLAIM);
    }

    /** Polite acknowledgements, instructions, questions, conditions and words that only look alike: no claim. */
    @ParameterizedTest
    @ValueSource(strings = {
        // 1A: "ghi nhận" means "noted"; a subject before "đã"; an object or other words after the verb; a question.
        "Em đã ghi nhận ạ",
        "Dạ, em đã ghi nhận yêu cầu của anh",
        "Đã ghi nhận.",
        "Báo giá đã được tạo, bạn xem bên dưới",
        "Đã tạo báo giá cho anh, anh xem bên dưới",
        "Dạ, đã tạo xong báo giá",
        "Dạ, em đã xác nhận lại thông tin xe rồi ạ",
        "Vâng, em đã nhận được thông tin xe",
        "Đã xác nhận chưa ạ?",
        "Đã xác nhận ạ?",
        "Dạ, xác nhận nhé",
        "Dạ xác nhận nhé",
        "da, xac nhan nhe",
        "Dạ, anh bấm xác nhận ở nút bên dưới",
        "Dạ, đã chốt đơn chưa ạ?",
        "Bạn đã duyệt rồi ạ?",
        "Đã duyệt rồi anh nhé?",
        "Nếu đã được duyệt rồi, anh chờ nhé.",
        "Hãy xác nhận rồi nhé.",
        "Dạ, em xác nhận nhé.",
        "Đã xác nhận chưa anh",
        "Đã lên lịch giao hàng cho anh",
        "Da xac nhan nhe",
        "Lưu ý: đã tạo báo giá cho anh",
        "Được, anh xác nhận ở nút bên dưới nhé",
        // 2A: a question, a condition, a punctuation mark between, "đơn giá", "ổn định".
        "Đơn ok không anh?",
        "Đơn của anh ok chưa ạ?",
        "Xong đơn chưa ạ?",
        "Nếu xong đơn, anh bấm gửi",
        "Khi đơn ok, nhân viên sẽ gọi",
        "Ok, đơn sẽ được nhân viên duyệt",
        "Đơn giá ổn",
        "Đơn ổn định không?",
        "don ok khong?",
        // 2A, review security M4.
        "Đơn xong thì anh bấm gửi",
        "Đơn hàng ổn thì nhân viên gọi anh",
        "Ok đơn của anh sẽ được nhân viên duyệt",
        "ok don cua anh se duoc nhan vien duyet",
        // 4A, review security L1: "tối đa" with a verb the copy does not check ("đặt").
        "Mỗi đơn tối đa đặt 10 sản phẩm",
        // 4A: words that start with "Đa"/"Đà".
        "Đa số khách chọn mã này",
        "Đà Nẵng có giao hàng"
    })
    void noClaimPasses(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    /**
     * Documented gaps (A-83): a claim without "đơn" after a comma ("Anh yên tâm, đã…"); "on" for "ổn" on the copy
     * without diacritics; "Dạ"/"Đã" first in a sentence without "được" on the copy ("Dạ duyệt rồi", and "Đã duỵêt rồi ạ"
     * with a misspelled verb); a verb the rule does not know ("chạy"). More than 60 characters between "đơn" and "đã"
     * is in {@code OutputValidatorOrderClaimTest}.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "Anh yên tâm, đã được duyệt rồi", "Don on roi", "Dạ duyệt rồi", "Đã duỵêt rồi ạ", "Đơn hàng chạy rồi ạ"
    })
    void documentedGapsAreNotBlocked(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    /** Accepted false positives (A-83): blocked and handed to staff. */
    @ParameterizedTest
    @ValueSource(strings = {
        "Đã xác nhận rồi ạ, anh chờ chút",
        "Điền xong đơn thì bấm gửi",
        "Đơn hàng sẽ hiện trong mục Lịch sử sau khi anh đã xác nhận báo giá",
        "Mở đơn trong mục Lịch sử, bạn sẽ thấy trạng thái đã xác nhận hay chưa",
        // QA review M3: a quote, not an order, without its object.
        "Đã tạo xong.",
        "Đã tạo xong ạ",
        // Security review M3: the window reaches an "đã" about the quote or about the customer.
        "Để đặt đơn, anh bấm nút Xác nhận trên báo giá em đã tạo",
        "Đơn hàng anh đã đặt đang chờ nhân viên duyệt",
        // Security review L1: "tối đa" reads as "tôi đã" on the copy without diacritics, which must keep "Tôi đã duỵêt
        // đơn" blocked (OutputValidatorDiacriticVariantsTest).
        "Mỗi đơn tối đa giao 10 sản phẩm",
        "Đơn hàng tối đa xử lý trong 2 ngày"
    })
    void acceptedFalsePositivesAreBlocked(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).contains(Rejection.ORDER_CLAIM);
    }

    /** The new shapes stay fast on the longest allowed answer. */
    @ParameterizedTest
    @ValueSource(strings = {
        "Dạ, ", "dạ vâng ", "đã được ", "Đã. ", "đơn hàng của ", "xong đơn ", "da, da duoc ", "ok ", "Đơn của a b ",
        "đơn ", "xong ", ". Dạ, ", "\n", " ", "- ", "đã duyệt đơn ", "đã duyệt rồi anh "
    })
    void longAnswersStayFast(String piece) {
        String text = piece.repeat(OutputValidator.MAX_CHARS / piece.length());
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> OutputValidator.check(text, NO_CODES));
        String nfd = Normalizer.normalize(text, Normalizer.Form.NFD);
        String longest = nfd.substring(0, Math.min(nfd.length(), OutputValidator.MAX_CHARS));
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> OutputValidator.check(longest, NO_CODES));
    }

    /** A near miss repeated to the longest answer, and a verb followed by spaces only to the end. */
    @Test
    void nearMissStaysFast() {
        String piece = "Dạ, đã duyệt rồi anh ";
        String text = piece.repeat(OutputValidator.MAX_CHARS / piece.length() - 1) + "x".repeat(10);
        String spaces = "đã duyệt" + " ".repeat(OutputValidator.MAX_CHARS - 8);
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> OutputValidator.check(text, NO_CODES));
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> OutputValidator.check(spaces, NO_CODES));
    }
}
