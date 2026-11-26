package com.secureerp.agent.agent.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.agent.internal.OutputValidator.Rejection;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * OUTPUT-CONFUSABLES and OUTPUT-LENGTH-RAW (A-77). Letters from other scripts that look like Latin ones, non-ASCII
 * digits, stray combining marks and characters that show as a gap would slip past every keyword rule of the
 * validator, so an answer containing them is rejected as a whole (1A, fail closed: a blocked answer goes to staff).
 * Only ASCII letters and the Vietnamese alphabet are accepted (2A). Length is checked on the text the customer gets as
 * well as on the normalized one (3A). Every look-alike or invisible character is written as a Unicode escape so
 * an editor or a formatter cannot turn a case into plain ASCII.
 */
class OutputValidatorConfusablesTest {

    private static final Set<String> NO_CODES = Set.of();
    /** Vietnamese vowels without a tone: a ă â e ê i o ô ơ u ư y. */
    private static final String VOWELS = "aăâeêioôơuưy";
    /** The five tone marks: grave, acute, hook above, tilde, dot below. */
    private static final String TONES = "̣̀́̉̃";

    @ParameterizedTest
    @ValueSource(strings = {
        // Cyrillic letters that look like Latin ones, inside money and order words.
        "Bộ này 450к thôi",
        "Bộ này hai trіệu",
        "Bộ này hai triеu",
        "Đơn đã duуệt cho anh",
        "Сас mа phanh",
        "Bộ này 2 тр",
        // Greek.
        "Bộ này hai triεu",
        "Giá: mοt triệu",
        // Non-ASCII decimal digits: Arabic-Indic, extended Arabic-Indic, Devanagari, Thai.
        "Bộ này ٢ triệu",
        "Bộ này ۲۰۰ nghìn",
        "Bộ này २ triệu",
        "Bộ này ๒ triệu",
        // Other scripts.
        "Bộ này 两百万",
        "ราคา bộ này",
        // Latin-script look-alikes outside the Vietnamese alphabet: small capital K, dotless i, kra, eth for Đ,
        // O with stroke for 0.
        "Bộ này 450ᴋ thôi",
        "Bộ này hai trıệu",
        "Bộ này 450ĸ thôi",
        "ÐƠN ÐÃ DUYỆT",
        "Bộ này 1ØØ nghìn",
        // Combining marks that do not compose with the letter before them split a keyword; one at the start or end.
        "Bộ này hai tr̶iệu",
        "Bộ này hai t̸riệu",
        "̶Bộ này",
        "Bộ này̶",
        // Review security H1: a Braille blank shows as a space but is none for the rules, in each keyword rule.
        "Bộ này 5⠀triệu",
        "Bộ này hai⠀triệu",
        "đơn hàng đã⠀được duyệt",
        "còn⠀5",
        // Review security L1: private use, unassigned code point, a lone surrogate.
        "Bộ này 5triệu",
        "Bộ này 5͸triệu",
        "Bộ này 5\uD800triệu",
        "Bộ này 5\uDC00triệu",
        // Review security L2: CJK full stops a browser may read as a dot in a link.
        "Đặt tại shop。com",
        "Đặt tại shop｡com"
    })
    void lookAlikeAndInvisibleCharactersAreRejected(String text) {
        assertThat(text).isNotEqualTo(text.replaceAll("[^\\x00-\\x7F]", ""));
        assertThat(OutputValidator.check(text, NO_CODES)).contains(Rejection.CONFUSABLE_SCRIPT);
    }

    /** NFKC already folds these to ASCII, so the money rule sees them; a variation selector is dropped first. */
    @ParameterizedTest
    @ValueSource(strings = {
        "Bộ này ２ triệu",
        "Bộ này 𝟐 triệu",
        "Bộ này ② triệu",
        "Bộ này 450𝐤",
        "Bộ này hai tr️iệu",
        "️Bộ này 2 triệu"
    })
    void compatibilityFormsAreFoldedThenCheckedForMoney(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).contains(Rejection.MONEY);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Xin chào! Bạn vui lòng cho biết mã OE hoặc số VIN của xe.",
        "Which brake pads fit a 2019 Vios?",
        "Má phanh – loại “chuẩn” (OEM) ✔ 👍 nhiệt độ 120°C,"
                + " kích thước 2×3",
        "Bạn thích không 👍️ nhé",
        "Hàng về trong 2–3 ngày; bạn kiểm tra giúp… nhé!"
    })
    void vietnameseAndEnglishPass(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).isEmpty();
    }

    /** Review QA M1: all 146 Vietnamese letters (12 vowels with 6 tones and đ, both cases), composed and decomposed. */
    @Test
    void everyVietnameseLetterPassesComposedAndDecomposed() {
        List<String> letters = new ArrayList<>();
        for (char vowel : VOWELS.toCharArray()) {
            letters.add(String.valueOf(vowel));
            for (char tone : TONES.toCharArray()) {
                String letter = Normalizer.normalize(vowel + String.valueOf(tone), Normalizer.Form.NFC);
                assertThat(letter).as("precomposed form exists").hasSize(1);
                letters.add(letter);
            }
        }
        letters.add("đ");
        List<String> both = new ArrayList<>(letters);
        letters.forEach(letter -> both.add(letter.toUpperCase(Locale.ROOT)));
        assertThat(both).hasSize(146).doesNotHaveDuplicates();

        for (String letter : both) {
            String sentence = "Chữ " + letter + " trong câu.";
            assertThat(OutputValidator.check(sentence, NO_CODES)).as(sentence).isEmpty();
            String decomposed = Normalizer.normalize(sentence, Normalizer.Form.NFD);
            assertThat(OutputValidator.check(decomposed, NO_CODES)).as(decomposed).isEmpty();
        }
    }

    /**
     * Accepted false positives (2A), rejected and handed to staff: Latin letters outside the Vietnamese alphabet (brand
     * names, "ß", O with stroke used for a diameter), symbols NFKC turns into Greek (micro sign, ohm sign), a keycap
     * emoji (its enclosing mark) and a modifier apostrophe.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "Xe Škoda Octavia dùng loại này",
        "Citroën C3 dùng loại này",
        "Ống dài 5 µm",
        "Cảm biến 10 kΩ",
        "Ống Ø 25 mm",
        "Straße",
        "Chọn 1️⃣ nhé",
        "Xe của anhʼs"
    })
    void acceptedFalsePositivesAreRejected(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).contains(Rejection.CONFUSABLE_SCRIPT);
    }

    @Test
    void decomposedTextLongerThanTheLimitIsRejected() {
        // "ệ" decomposed is three code units; NFKC makes it one, so the normalized text is 1000 characters long.
        String nfd = Normalizer.normalize("ệ", Normalizer.Form.NFD).repeat(1000);
        assertThat(nfd).hasSize(3000);
        assertThat(OutputValidator.check(nfd, NO_CODES)).contains(Rejection.TOO_LONG);
    }

    @Test
    void decomposedTextAtTheLimitPasses() {
        String nfd = Normalizer.normalize("ệ", Normalizer.Form.NFD).repeat(666) + "ab";
        assertThat(nfd).hasSize(OutputValidator.MAX_CHARS);
        assertThat(OutputValidator.check(nfd, NO_CODES)).isEmpty();
    }

    @Test
    void decomposedTextOneOverTheLimitIsRejected() {
        String nfd = Normalizer.normalize("ệ", Normalizer.Form.NFD).repeat(666) + "abc";
        assertThat(nfd).hasSize(OutputValidator.MAX_CHARS + 1);
        assertThat(OutputValidator.check(nfd, NO_CODES)).contains(Rejection.TOO_LONG);
    }

    /** Review QA M5: the ellipsis is one character that NFKC turns into three dots. */
    @Test
    void expandedTextAtTheLimitPasses() {
        String text = "…".repeat(666) + "ab";
        assertThat(Normalizer.normalize(text, Normalizer.Form.NFKC)).hasSize(OutputValidator.MAX_CHARS);
        assertThat(OutputValidator.check(text, NO_CODES)).isEmpty();
    }

    @Test
    void expandedTextOneOverTheLimitIsRejected() {
        String text = "…".repeat(667);
        assertThat(text).hasSizeLessThan(OutputValidator.MAX_CHARS);
        assertThat(OutputValidator.check(text, NO_CODES)).contains(Rejection.TOO_LONG);
    }
}
