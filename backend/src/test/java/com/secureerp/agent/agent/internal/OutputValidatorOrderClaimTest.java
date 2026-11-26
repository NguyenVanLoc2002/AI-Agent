package com.secureerp.agent.agent.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.secureerp.agent.agent.internal.OutputValidator.Rejection;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * OUTPUT-ORDER-CLAIM-MORE (A-82). The model must never say that an order was approved, created or handled: only staff
 * and the backend do that (CLAUDE.md). The rule only knew the claim with nothing between "đơn" and the verb and a few
 * verbs. It now allows up to 30 characters between them in the same sentence (1A), knows more verbs (2A) and applies
 * both to the copy without diacritics (3A). Only the words right after "đơn" or the verb make it no claim: a negation,
 * the future or a wait ("Đơn chưa được duyệt"), a condition right before "đơn", a question "…chưa?". Instructions on
 * how to order must still pass. Both the rule with diacritics and the copy without diacritics see correctly spelled
 * text; cases marked "with diacritics only" are those the copy cannot see ("da" needs a word before it there).
 */
class OutputValidatorOrderClaimTest {

    private static final Set<String> NO_CODES = Set.of();

    @ParameterizedTest
    @ValueSource(strings = {
        // Words between "đơn" and the verb.
        "Đơn hàng của anh đã được duyệt",
        "Đơn #123 đã được xác nhận",
        "Đơn má phanh của chị đã được duyệt rồi",
        "Đơn hàng của anh đã được xác nhận và chuyển kho",
        "Đơn hàng đã đặt. Nhân viên sẽ xác nhận sau",
        // Review security H1: words that only look like a negation, a condition or a wait.
        "Đơn hàng mà anh cần đã được duyệt",
        "Đơn hàng khi nãy đã được duyệt rồi ạ",
        "Đơn không có vấn đề gì, đã xử lý xong",
        "Đơn của anh đang ổn, đã chốt rồi",
        "Đơn cho anh đã được duyệt",
        // Review security H2: a trailing question does not undo a claim.
        "Đơn đã duyệt rồi, anh cần gì thêm chưa?",
        "Em đã duyệt đơn rồi, anh muốn hỏi gì nữa chưa?",
        "Bạn đã xác nhận đơn chưa? Đơn đã duyệt",
        "Nếu cần, đơn đã được duyệt",
        // Review security M1: who did it, or "rồi"/"xong" without "đã".
        "Đơn của anh đã được bên em duyệt rồi",
        "Đơn được duyệt rồi ạ",
        "Đơn hàng được xử lý xong rồi",
        // New verbs.
        "Đơn của bạn đã xử lý xong",
        "Em đã xử lý đơn cho anh",
        "Em đã lên đơn cho anh rồi",
        "Bên em đã ghi nhận đơn của anh",
        "Đơn hàng đã hoàn tất",
        "Đơn của anh đã được giao",
        "Đơn đã thanh toán xong",
        "Anh đã đặt hàng thành công",
        "Đặt đơn thành công!",
        "Tạo đơn thành công",
        "Lên đơn thành công rồi anh nhé",
        // No subject: with diacritics only.
        "Đã lên đơn cho anh rồi",
        "Đã ghi nhận đơn của anh",
        "Đã hoàn tất đơn của anh",
        "Đã xử lý đơn cho anh",
        "Đã giao đơn cho anh",
        "Đã thanh toán đơn xong",
        "Đã chốt đơn cho anh",
        "Đã tạo đơn cho anh",
        "Đã duyệt đơn cho anh",
        "Đã được duyệt đơn",
        "Đã duyệt đơn rồi anh cần gì thêm chưa?",
        // "đặt" is not checked on the copy without diacritics (A-78), so only the rule with diacritics sees these.
        "Đơn đã được nhân viên đặt giúp anh",
        "Đơn được đặt rồi ạ",
        // A misspelled verb: only the copy without diacritics sees these.
        "Đơn đã được bên em duỵêt",
        "Đơn được duỵêt rồi ạ",
        // Without diacritics (ASCII).
        "don hang cua anh da duoc duyet",
        "em da len don cho anh",
        "don cua anh da duoc ben em duyet roi",
        // Misspelled: the copy without diacritics only.
        "Đơn hàng của anh đã được duỵêt",
        "Em đã lển đơn cho anh",
        "Đơn hàng đã hoàn tât",
        "Anh đã đặt hàng thanh công",
        "Đơn của bạn đã xử lí xong",
        // English.
        "We have placed your order",
        "Your order has been processed",
        "Your order has been created",
        "Your order has been placed",
        "Order #123 confirmed",
        "Order confirmed",
        "Your order was shipped",
        "Your order is completed",
        "We approved your order",
        "We confirmed the order",
        "Your order's been approved",
        "Your order’s been approved",
        "order: confirmed",
        "Your order has now been approved",
        "Your order has been successfully approved",
        "Your order is now confirmed",
        "Your order got approved"
    })
    void orderClaimsAreRejected(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).contains(Rejection.ORDER_CLAIM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Đơn hàng của anh đã được duyệt", "Đơn được duyệt rồi ạ"})
    void decomposedClaimIsRejected(String text) {
        String nfd = Normalizer.normalize(text, Normalizer.Form.NFD);
        assertThat(OutputValidator.check(nfd, NO_CODES)).contains(Rejection.ORDER_CLAIM);
    }

    /** How to order, what happens next, a negation, a condition or a question: no claim. */
    @ParameterizedTest
    @ValueSource(strings = {
        "Bạn đặt đơn ở nút bên dưới nhé",
        "Bạn muốn lên đơn không?",
        "Sau khi bạn xác nhận, đơn sẽ được nhân viên duyệt",
        "Đơn chưa được duyệt",
        "Đơn của bạn chưa được duyệt, vui lòng chờ",
        "Đơn của bạn đang chờ nhân viên duyệt",
        "Đơn của bạn sẽ được xử lý trong giờ làm việc",
        "Đơn đặt hàng sẽ được tạo sau khi bạn xác nhận",
        "Đơn được duyệt thì nhân viên gọi anh",
        "Nhân viên sẽ duyệt đơn trong giờ làm việc",
        "Sau khi nhân viên duyệt đơn, bạn sẽ nhận thông báo",
        "Tôi không thể duyệt đơn, nhân viên sẽ duyệt",
        "Giao hàng trong ngày sau khi đơn được duyệt",
        "Đơn hàng của bạn chưa thanh toán",
        "Giao tận nơi, thanh toán khi nhận hàng",
        "Bạn đã nhận được báo giá chưa?",
        "Báo giá đã được tạo, bạn xem bên dưới",
        "Đơn ở bên dưới. Báo giá đã được tạo",
        "Đơn của bạn đã được gửi tới nhân viên để duyệt",
        // Each word right after "đơn" that makes it no claim, before an "đã" about the customer.
        "Đơn chưa duyệt dù anh đã thanh toán",
        "Đơn sẽ được duyệt sau, anh đã xác nhận rồi",
        "Đơn đang đợi dù anh đã xác nhận",
        "Đơn chờ duyệt, anh đã xác nhận rồi",
        "Đơn đợi duyệt, anh đã xác nhận rồi",
        "Đơn của chị chưa duyệt dù anh đã xác nhận",
        "don chua duyet du anh da thanh toan",
        "don cho duyet, anh da xac nhan roi",
        // A condition right before "đơn".
        "Nếu đơn đã được duyệt, nhân viên sẽ báo anh",
        "Khi đơn hàng của anh đã được duyệt, nhân viên sẽ gọi",
        "Lúc đơn đã được duyệt, anh sẽ nhận tin",
        "neu don da duoc duyet, nhan vien se bao anh",
        // A question right after "đơn" or the verb.
        "Bạn đã xác nhận đơn chưa?",
        "Anh đã thanh toán đơn trước chưa ạ?",
        "Đơn của anh đã được duyệt chưa?",
        "ban da xac nhan don chua?",
        // "Đơn" that is not an order.
        "Đơn vị vận chuyển đã giao hàng tới kho",
        "Đơn giản thôi, bạn chỉ cần bấm nút xác nhận",
        "Đơn giản là em đã xác nhận lại thông tin xe",
        // "Thành công" after a future, a condition or a need.
        "Để đặt hàng thành công, bạn cần xác nhận báo giá",
        "Bạn sẽ đặt hàng thành công khi bấm xác nhận",
        "Nếu đặt hàng thành công, bạn nhận tin nhắn",
        "Khi đặt hàng thành công, bạn nhận tin nhắn",
        "Bạn cần đặt hàng thành công trước",
        "Bạn có thể đặt hàng thành công ngay",
        // English.
        "Your order will be confirmed by staff",
        "Once your order is confirmed, staff will contact you",
        "After your order is approved, we deliver in a day",
        "If your order is approved, staff will call",
        "When your order is confirmed, you get a message",
        "Until your order is confirmed, nothing is charged",
        "Before your order is approved, staff check the stock",
        "Order not confirmed yet",
        "Your order has not been approved",
        "Place your order below",
        "Dạ, chốt đơn ở nút bên dưới giúp em"
    })
    void instructionsAndNegationsPass(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Đơn của bạn chưa được duyệt", "Nếu đơn đã được duyệt, nhân viên sẽ báo anh"})
    void decomposedNegationPasses(String text) {
        String nfd = Normalizer.normalize(text, Normalizer.Form.NFD);
        assertThat(OutputValidator.check(nfd, NO_CODES)).isEmpty();
    }

    /**
     * QA review: at most 30 characters between "đơn" and "đã". "đã đặt" is only seen by the rule with diacritics, "đã
     * được duỵêt" only by the copy without diacritics, "đã được duyệt" by both.
     */
    @ParameterizedTest
    @ValueSource(ints = {29, 30})
    void claimWithinTheWindowIsRejected(int gap) {
        String filler = " " + "x".repeat(gap - 2) + " ";
        for (String claim : new String[] {"đã đặt", "đã được duỵêt", "đã được duyệt"}) {
            String text = "Đơn" + filler + claim;
            assertThat(OutputValidator.check(text, NO_CODES)).as(text).contains(Rejection.ORDER_CLAIM);
        }
        assertThat(OutputValidator.check("don" + filler + "da duoc duyet", NO_CODES)).contains(Rejection.ORDER_CLAIM);
    }

    @ParameterizedTest
    @ValueSource(ints = {31})
    void claimBeyondTheWindowPasses(int gap) {
        String filler = " " + "x".repeat(gap - 2) + " ";
        for (String claim : new String[] {"đã đặt", "đã được duỵêt", "đã được duyệt"}) {
            String text = "Đơn" + filler + claim;
            assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
        }
        assertThat(OutputValidator.check("don" + filler + "da duoc duyet", NO_CODES)).isEmpty();
    }

    /**
     * Documented gaps (review security M1, M3, L1): a claim without "đơn", without a verb the rule knows, with more than
     * 30 characters between, or a misspelled "đã" at the start of a sentence. Changing one is an owner decision.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "Đã được duyệt rồi ạ", "Xong đơn rồi", "Đơn OK rồi",
        "Đơn hàng anh gửi bên em hôm qua lúc chiều, em báo là đã được duyệt", "Đả duyệt đơn rồi"
    })
    void documentedGapsAreNotBlocked(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    /** Accepted false positives (review security L2): blocked and handed to staff. */
    @ParameterizedTest
    @ValueSource(strings = {
        "Mở đơn, kiểm tra mục đã xác nhận", "Sau đó đơn đã tạo sẽ hiện ở mục Lịch sử", "Chúc anh đặt hàng thành công"
    })
    void acceptedFalsePositivesAreBlocked(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).contains(Rejection.ORDER_CLAIM);
    }

    /** The window and the lookarounds stay fast on the longest allowed answer. */
    @ParameterizedTest
    @ValueSource(strings = {"đơn ", "đơn của anh đã ", "đặt hàng ", "don cua anh da ", "đơn đơn đơn đã ", "order "})
    void longAnswersStayFast(String piece) {
        String text = piece.repeat(OutputValidator.MAX_CHARS / piece.length());
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> OutputValidator.check(text, NO_CODES));
    }
}
