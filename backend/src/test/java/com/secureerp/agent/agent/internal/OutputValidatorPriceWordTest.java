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
 * OUTPUT-PRICE-WORD-BOUNDARY (A-79). The rule "price word, then a digit within 20 characters" read any word that starts
 * with a price word as one ("gian", "giám", "giao", "giai", "giáp") and looked past the end of the sentence. A price
 * word is now a whole word (1A) and the digit must be in the same sentence (2A): a full stop, "!", "?" or a line break
 * ends the sentence only when an upper-case letter starts the next one. The words without diacritics stay (3A, fail
 * closed), so "tham gia 2…" is still blocked.
 */
class OutputValidatorPriceWordTest {

    private static final Set<String> NO_CODES = Set.of();

    /** 1A: words that start or end with a price word are not price words. */
    @ParameterizedTest
    @ValueSource(strings = {
        "Thời gian bảo hành 12 tháng",
        "Hệ thống giám sát 2 cảm biến",
        "Giao tới 2 tỉnh",
        "Giao hàng trong 2 ngày",
        "Giai đoạn 2 sẽ có hàng",
        "Ốp giáp 2 bên cửa",
        "Reprice 2 items"
    })
    void wordsThatContainAPriceWordPass(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    /** "giảm xóc" (shock absorber) and "giảm chấn" (damper) are part names, not a discount. */
    @ParameterizedTest
    @ValueSource(strings = {
        "Giảm xóc 2 bên đều mòn",
        "Giảm chấn 2 bên cần thay",
        "GIẢM XÓC 2 BÊN",
        "Giam xoc 2 ben deu mon"
    })
    void partNamesWithGiamPass(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    /** 2A: the digit is in a new sentence that starts with an upper-case letter. */
    @ParameterizedTest
    @ValueSource(strings = {
        "Giá hỏi nhân viên. Xe 2 cầu dùng loại này",
        "Giá hỏi nhân viên! Xe 2 cầu",
        "Giá hỏi nhân viên? Xe 2 cầu",
        "Giá hỏi nhân viên\nXe 2 cầu",
        "Giá hỏi nhân viên.\n\nĐời 2019 dùng loại này"
    })
    void digitInTheNextSentencePasses(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    /** The rule still catches a price word followed by a digit, whatever sits between them in the same sentence. */
    @ParameterizedTest
    @ValueSource(strings = {
        "Giá:500",
        "Giá500",
        "Giá 950",
        "Giá khoảng 950 thôi",
        "Giá, 950",
        "Giá—950",
        "Giá\t950",
        "(Giá) 950",
        "GIÁ 950",
        "Gía 950",
        "Gìa 950",
        "Gỉa 950",
        "Gĩa 950",
        "Gịa 950",
        "Gia 950",
        "Tổng: 900",
        "TỔNG 900",
        "Tong 900",
        "Giảm 50 cho anh",
        "Giam 50",
        "Giảm giá 50",
        "Giảm thanh lý 30%",
        "Giảm thanh toán 20%",
        "Giam toc 15%",
        "Thành tiền 900",
        "Thanh tien 900",
        "Chiết khấu 10",
        "Chiet khau 10",
        "Price: 95",
        "PRICE 95",
        "price95",
        "Má phanh giá 950",
        "Giá thanh lý 500",
        "Giảm giá xóc 50",
        // Review security M2: a sentence break that does not start a new sentence is no stop.
        "Giá. 500",
        "Giá.950",
        "Giá?950",
        "Giá!950",
        "Giá… 950",
        "Giá． 950",
        "Giá:\n950",
        "Tổng. 1200",
        "Price.\n45",
        "giảm!20%",
        "giá hỏi nhân viên. xe 2 cầu"
    })
    void priceWordAndDigitIsRejected(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).contains(Rejection.MONEY);
    }

    @Test
    void decomposedPriceWordIsRejected() {
        String nfd = Normalizer.normalize("Giá 950", Normalizer.Form.NFD);
        assertThat(OutputValidator.check(nfd, NO_CODES)).contains(Rejection.MONEY);
    }

    /** The digit may be at most 20 characters after the price word. */
    @Test
    void digitTwentyCharactersAfterIsRejected() {
        String text = "Giá" + " hỏi".repeat(5) + "9";
        assertThat(text.indexOf('9') - "Giá".length()).isEqualTo(20);
        assertThat(OutputValidator.check(text, NO_CODES)).contains(Rejection.MONEY);
    }

    @Test
    void digitTwentyOneCharactersAfterPasses() {
        String text = "Giá" + " hỏi".repeat(5) + " 9";
        assertThat(OutputValidator.check(text, NO_CODES)).isEmpty();
    }

    /**
     * 3A, accepted false positives: price words without diacritics are also parts of common phrases ("tham gia", "gia
     * đình", "gia công", "gia hạn"), "tổng" and "giá" begin "tổng đài", "tổng quan", "tổng cộng", "giá trị", "giảm
     * thanh" (muffler) and "giảm tốc" (reducer) are kept because "giảm thanh lý 30%" is a discount, and a new sentence
     * that starts in lower case does not end the old one. Blocked and handed to staff.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "Tham gia 2 chương trình", "Gia đình bạn có 2 xe", "Gia công 2 mặt", "Gia hạn bảo hành 2 năm",
        "Tổng đài 1900", "Tổng quan: xe 2 cầu", "Tổng cộng 2 bộ", "Giá trị áp suất 2 bar", "Ống giảm thanh 2 lớp",
        "Hộp giảm tốc 2 cấp", "Giá hỏi nhân viên. xe 2 cầu"
    })
    void acceptedFalsePositivesAreBlocked(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).contains(Rejection.MONEY);
    }

    /** Documented gap (review security L1): a price word glued to another word is not a whole word. */
    @ParameterizedTest
    @ValueSource(strings = {"Giábán 950", "xGiá 950"})
    void documentedGapsAreNotBlocked(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    /** Review security: the sentence-aware window stays fast on the longest allowed answer. */
    @ParameterizedTest
    @ValueSource(strings = {"giá ", "giá. ", "giá.\n", "tổng! "})
    void longAnswersStayFast(String piece) {
        String text = piece.repeat(OutputValidator.MAX_CHARS / piece.length());
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> OutputValidator.check(text, NO_CODES));
    }
}
