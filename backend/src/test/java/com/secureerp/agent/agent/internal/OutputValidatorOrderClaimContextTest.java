package com.secureerp.agent.agent.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.secureerp.agent.agent.internal.OutputValidator.Rejection;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * OUTPUT-ORDER-CLAIM-CONTEXT (A-90). (1A) When the turn is about an order or a quote, done claims are rejected
 * without "đơn" and without the 60-character window; (2A) the context comes from the customer messages or a quote
 * proposal; (3A) the claim is "đã" then an order verb, or an order verb then "rồi/xong/ok/thành công", except right
 * after a condition, a negation, a future or a request, right before "chưa", or in a short question.
 */
class OutputValidatorOrderClaimContextTest {

    private static final Set<String> NO_CODES = Set.of();

    private static boolean context(String text) {
        return OutputValidator.check(text, NO_CODES, true).filter(r -> r == Rejection.ORDER_CLAIM).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        // The A-83 gaps (OutputValidatorOrderClaimRestTest.documentedGapsAreNotBlocked).
        "Anh yên tâm, đã được duyệt rồi", "Dạ duyệt rồi", "Đã duỵêt rồi ạ", "Đơn hàng chạy rồi ạ",
        // More than 60 characters between "đơn" and the verb.
        "Đơn của anh gồm hai bộ má phanh trước, một bộ lọc gió và một bộ dầu máy, đã được duyệt",
        // Other shapes.
        "Đã xác nhận cho anh rồi ạ", "Bên em đã chốt rồi nhé", "Em đã giao cho anh rồi", "Thanh toán thành công",
        "Đã được nhân viên duyệt", "Đã phê duyệt", "Xử lý xong rồi ạ", "Dạ, đã lên đơn cho anh", "Duyệt ok rồi anh",
        "Cảm ơn anh. Đã duyệt rồi ạ.", "Dạ vâng, xác nhận xong rồi", "ĐÃ DUYỆT RỒI", "Đã được duyệt!",
        "Duyệt rồi, anh chờ giao hàng nhé", "Em kiểm tra rồi, đã chốt cho anh", "Không sao, đã duyệt rồi",
        // Review security H1, H2, backend M2, QA M6, M7: a question or an exemption word further away.
        "Đã duyệt rồi anh cần thêm gì không ạ?", "Đơn đã giao rồi anh nhận được chưa?", "Em xem rồi đã được duyệt rồi ạ",
        "Lúc nãy đã được xác nhận rồi", "Em đang kiểm tra thấy đã duyệt rồi", "Anh không phải lo đã duyệt rồi",
        "Anh chờ chút nhé – đã duyệt rồi", "Anh không cần lo đã duyệt rồi", "Em xem rồi đã chốt",
        "Đã duyệt rồi nhưng chưa giao", "Nếu cần anh báo em - đã duyệt rồi", "Khi anh hỏi thì đã duyệt rồi",
        "Anh sẽ nhận hàng vì đã duyệt rồi", "Đã duyệt rồi nhé, nếu cần em báo", "đã\nduyệt rồi", "đã, duyệt rồi",
        // Without diacritics.
        "da duyet roi", "Da xac nhan roi a", "duoc duyet roi nhe", "Anh yen tam, da duoc duyet roi", "chot xong roi",
        // Review security M3: typed without any diacritics, "cho"/"moi" are not exemptions.
        "Don cho anh da duoc duyet roi", "Don moi da duoc duyet roi", "Moi anh da duyet roi", "Anh cho da duyet",
        // Mutation review: only the context rule blocks these (the A-83 rules pass them).
        "Nếu đã duyệt thì tốt và đã chốt", "Bên em đã nhanh chóng duyệt", "Em đã kịp duyệt", "Bên em vừa duyệt xong",
        "Hàng đã được chốt", "Em xác nhận xong rồi nhé", "anh yen tam da chot"
    })
    void doneClaimsAreRejectedInAnOrderContext(String text) {
        assertThat(context(text)).as(text).isTrue();
    }

    /** The same text without an order context: the A-83 rules only. */
    @ParameterizedTest
    @ValueSource(strings = {"Anh yên tâm, đã được duyệt rồi", "Dạ duyệt rồi", "Đã duỵêt rồi ạ", "Đơn hàng chạy rồi ạ"})
    void withoutContextTheyStayGaps(String text) {
        assertThat(OutputValidator.check(text, NO_CODES, false)).as(text).isEmpty();
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    /** Conditions, negations, futures, requests, "chưa" and questions; texts without a claim. */
    @ParameterizedTest
    @ValueSource(strings = {
        "Anh đã xác nhận chưa?", "Đơn sẽ được duyệt trong hôm nay", "Nhân viên sẽ xác nhận và giao hàng",
        "Nếu anh đồng ý, anh bấm Xác nhận", "Khi được duyệt, em sẽ báo anh", "Anh vui lòng bấm Xác nhận trên báo giá",
        "Em chưa thể xác nhận", "Anh xem đã duyệt chưa trong mục Lịch sử", "Báo giá đã được gửi bên dưới.",
        "Phụ tùng này giao trong 2 ngày", "Cầu chì đã cháy thì thay mới", "Cau chi da chay thi thay moi",
        "Em không xác nhận được", "Để chốt, anh bấm nút bên dưới", "Bộ này phù hợp xe anh",
        "Dạ, anh bấm Xác nhận để chốt", "Anh cần xác nhận rồi nhân viên mới duyệt", "Hãy xác nhận rồi em báo",
        "Đang chờ duyệt ạ", "Khi giao hàng xong, anh kiểm tra giúp em", "Anh đã nhận được báo giá chưa ạ",
        "Lúc giao xong anh gọi em", "Mời anh xác nhận rồi chờ nhân viên", "Đã duyệt chưa anh nhỉ",
        "Em đã gửi báo giá ở trên", "Anh xem báo giá, sau đó bấm xác nhận", "Dạ em hiểu rồi", "Đã được duyệt rồi à?",
        "Đã duyệt rồi chưa ạ", "Khi anh đã xác nhận, em báo kho",
        // Review security H3: ordinary parts-sales answers.
        "Dạ bên em có giao hàng toàn quốc", "Dạ giao trong 2 ngày ạ", "Hàng được giao trong 2 ngày",
        "Sản phẩm được giao miễn phí", "Mã OE này đã được xác nhận phù hợp với xe anh", "Bộ má phanh đã kèm chốt",
        "Bộ này đã có chốt piston", "Xe đã chạy 60 km thì kiểm tra", "Xe đã chạy được 3 năm",
        "Da ben em co giao hang toan quoc", "Da giao trong 2 ngay a",
        "Đã xác nhận là đúng mã", "Xe chạy êm rồi",
        // Mutation review: each exemption and exclusion on its own.
        "Nhân viên sẽ duyệt rồi báo anh", "Hàng được xác nhận chính hãng", "Chốt piston xong thì lắp lại",
        "Anh đang duyệt rồi à", "Mời anh duyệt rồi", "Khi duyệt rồi em báo", "Lúc duyệt xong em báo",
        "Anh xem đã chốt", "Đã kiểm tra kỹ và chốt"
    })
    void ordinaryTextPassesInAnOrderContext(String text) {
        assertThat(OutputValidator.check(text, NO_CODES, true)).as(text).isEmpty();
    }

    /**
     * Documented gaps (A-90): verbs not in the list ("ổn", "xong" alone, "hoàn tất"); more than three words between
     * "đã" and the verb; English claims beyond A-82; "được" without "đã" and without "rồi/xong"; "chạy" without
     * diacritics (cháy); a claim right after an exemption word ("sau khi anh đã…" is a condition); a quote from an
     * earlier turn (DefaultAgent).
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "Ổn rồi anh nhé", "Xong rồi ạ", "Đã được bên em và nhân viên kho duyệt", "All approved now",
        "Da chay roi", "Không đâu, chưa đã duyệt"
    })
    void documentedGapsAreNotBlocked(String text) {
        assertThat(OutputValidator.check(text, NO_CODES, true)).as(text).isEmpty();
    }

    /** Accepted false positives (A-90): blocked and handed to staff. */
    @ParameterizedTest
    @ValueSource(strings = {
        "Giao hàng xong anh kiểm tra giúp em", "Xác nhận xong anh sẽ nhận hàng", "Em đã xử lý yêu cầu của anh rồi",
        "Tôi đã xác nhận rồi", "Em được biết xe đã giao rồi"
    })
    void acceptedFalsePositivesAreBlocked(String text) {
        assertThat(context(text)).as(text).isTrue();
    }

    /** Pre-existing (A-83), also without context: the existing rule is not narrowed. */
    @ParameterizedTest
    @ValueSource(strings = {
        "Đơn hàng sẽ hiện trong mục Lịch sử sau khi anh đã xác nhận báo giá", "Em đã giao tiếp với kho"
    })
    void theA83FalsePositivesStayBlocked(String text) {
        assertThat(OutputValidator.check(text, NO_CODES, false)).as(text).contains(Rejection.ORDER_CLAIM);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "đơn của tôi duyệt chưa", "Báo giá giúp tôi", "bao gia 2 cai", "Khi nào giao hàng?", "dat hang the nao",
        "Please approve the order", "Xác nhận giúp mình", "don hang cua toi", "Thanh toán thế nào?", "Lấy luôn 2 bộ",
        "ok em, đồng ý", "Chuyển khoản được không", "confirm giúp mình", "ship về Hà Nội", "lay luon 2 bo"
    })
    void customerMessagesAboutAnOrderTurnOnTheContext(String text) {
        assertThat(OutputValidator.orderTopic(List.of("má phanh trước", text))).as(text).isTrue();
    }

    @Test
    void decomposedTextAndNullMessagesAreHandled() {
        assertThat(OutputValidator.orderTopic(List.of(Normalizer.normalize("đơn của tôi", Normalizer.Form.NFD))))
                .isTrue();
        assertThat(OutputValidator.orderTopic(Arrays.asList(null, "má phanh"))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Tôi cần má phanh trước", "Xe Toyota Vios 2019", "toi muon don xe", "giao lưu", "Đơn giản thôi", "Giá bao nhiêu?"
    })
    void otherCustomerMessagesDoNot(String text) {
        assertThat(OutputValidator.orderTopic(List.of(text))).as(text).isFalse();
    }

    /** The clause scan stays fast on the longest allowed answer. */
    @ParameterizedTest
    @ValueSource(strings = {
        "chưa duyệt rồi ", "đã ", "duyệt ", "nếu đã duyệt rồi ", "da duoc ", "xem da duyet roi ", "anh đã có kèm ",
        "khi anh đã duyệt chưa "
    })
    void longAnswersStayFast(String piece) {
        String text = piece.repeat(OutputValidator.MAX_CHARS / piece.length());
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> OutputValidator.check(text, NO_CODES, true));
    }
}
