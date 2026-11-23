package com.secureerp.agent.policy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.policy.PolicyCode;
import com.secureerp.agent.policy.SentinelVariants;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** T-SEC01-VARIANTS (detector level) plus false-positive checks on ordinary technical text. */
class SensitiveDataDetectorTest {

    private final SensitiveDataDetector detector = new SensitiveDataDetector();

    static Stream<Arguments> sentinelVariants() {
        return Stream.of(
                        Map.entry("HT-COST-01", SentinelVariants.COST),
                        Map.entry("HT-CREDIT-01", SentinelVariants.CREDIT),
                        Map.entry("HT-PRICE-A", SentinelVariants.PRICE_A))
                .flatMap(token -> SentinelVariants.of(token.getValue()).entrySet().stream()
                        .map(v -> Arguments.of(token.getKey(), v.getKey(), v.getValue())));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("sentinelVariants")
    void everyVariantOfEverySentinelIsDetected(String honeytokenId, String variantId, String text) {
        assertThat(detector.detect(text))
                .as(variantId)
                .contains(new SensitiveDataDetector.Finding(PolicyCode.HONEYTOKEN, honeytokenId));
    }

    @Test
    void atLeastTwentyVariantsPerSentinel() {
        assertThat(SentinelVariants.of(SentinelVariants.COST)).hasSizeGreaterThanOrEqualTo(20);
    }

    @Test
    void sentinelSplitAcrossValuesIsDetected() {
        assertThat(detector.splitHoneytoken(List.of("ma 987", "loai 654", "so 321")))
                .contains(new SensitiveDataDetector.Finding(PolicyCode.HONEYTOKEN, "HT-COST-01"));
    }

    /** SEC03-F4 (A-57): a text honeytoken split across values, in payload order, is one token. */
    static Stream<Arguments> textHoneytokensSplitAcrossValues() {
        return Stream.of(
                Arguments.of("HT-NAME-A", "two turns", List.of("Người nhận là ZZHoney", "CustomerA, báo giá má phanh")),
                Arguments.of("HT-NAME-A", "three turns", List.of("Gửi ZZH", "oney Cust", "omerA nhé")),
                Arguments.of("HT-NAME-A", "cyrillic half", List.of("Người nhận là ZZHоney", "CustomerA nhé")),
                Arguments.of("HT-NAME-A", "customer and catalog", List.of("Cần má phanh cho ZZHoney", "Customer A")),
                Arguments.of("HT-NAME-A", "with values between", List.of("ZZHoney", "", "CustomerA")),
                Arguments.of("HT-NAME-A", "role between messages",
                        List.of("CUSTOMER", "Người nhận là ZZHoney", "CUSTOMER", "CustomerA nhé")),
                Arguments.of("HT-NAME-A", "sku and oe before the category",
                        List.of("Cần má phanh cho ZZHoney", "searchVerifiedProduct", "SKU-BRK-PAD-A", "04465-0K290",
                                "Customer A")),
                Arguments.of("HT-NAME-A", "reverse order", List.of("CustomerA nhé", "Người nhận là ZZHoney")),
                Arguments.of("HT-EMAIL-A", "email halves", List.of("Email tôi là honey.customer.a", "@example.invalid")),
                Arguments.of("HT-KEY-01", "key halves", List.of("mã apasht", "_key_abc")));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("textHoneytokensSplitAcrossValues")
    void aTextHoneytokenSplitAcrossValuesIsDetected(String honeytokenId, String variantId, List<String> values) {
        assertThat(detector.splitHoneytoken(values)).as(variantId)
                .contains(new SensitiveDataDetector.Finding(PolicyCode.HONEYTOKEN, honeytokenId));
        for (String value : values) {
            assertThat(detector.honeytoken(value)).as("%s alone", value).isEmpty();
        }
    }

    /** SEC03-F4 (A-57): joining ordinary values does not form a token; filler between pieces is a residual. */
    @Test
    void ordinaryValuesJoinedAreNotAHoneytoken() {
        assertThat(detector.splitHoneytoken(List.of("Người nhận là anh Nguyen", "Van An, báo giá má phanh giúp",
                "Ma phanh truoc", "04465-0K290", "searchVerifiedProduct", "CUSTOMER_REQUEST", "UNSURE", "OUT_OF_SCOPE",
                "Tìm sản phẩm đã xác minh theo mã OE"))).isEmpty();
        assertThat(detector.splitHoneytoken(List.of("ZZHoney x", "CustomerA"))).as("residual: filler").isEmpty();
        // the fixed role value is a whole middle piece; a one-letter edge must not complete the token (A-57 review)
        assertThat(detector.splitHoneytoken(List.of("Người nhận là ZZHoney", "CUSTOMER", "anh ơi"))).isEmpty();
        assertThat(detector.splitHoneytoken(List.of("ZZHoneyCustome", "rA"))).as("residual: short edge").isEmpty();
    }

    /** A-57 review: the edges are at least three characters; a middle piece may be any whole value. */
    @Test
    void assemblyNeedsEdgesOfThreeCharacters() {
        assertThat(SensitiveDataDetector.assembles("zzhoneycustomera", List.of("xzzh", "oney", "customera"))).isTrue();
        assertThat(SensitiveDataDetector.assembles("zzhoneycustomera", List.of("xzz", "honeycustomera"))).isFalse();
        assertThat(SensitiveDataDetector.assembles("zzhoneycustomera", List.of("zzhoneycustom", "era"))).isTrue();
        assertThat(SensitiveDataDetector.assembles("zzhoneycustomera", List.of("zzhoneycustome", "ra"))).isFalse();
    }

    @Test
    void textHoneytokensAreDetected() {
        assertThat(detector.detect("Khach ZZHONEY   customerA can phanh")).map(SensitiveDataDetector.Finding::honeytokenId)
                .contains("HT-NAME-A");
        assertThat(detector.detect("key=APASHT_KEY_abc")).map(SensitiveDataDetector.Finding::honeytokenId)
                .contains("HT-KEY-01");
    }

    /**
     * SEC03-F1 (A-55): look-alike letters, digits used as letters, accents and separators inside a text honeytoken,
     * in plain text, in one decoded layer and in a raw payload.
     */
    static Stream<Arguments> disguisedTextHoneytokens() {
        String base64 = Base64.getEncoder().encodeToString("ZZHоney CustomerA".getBytes(StandardCharsets.UTF_8));
        return Stream.of(
                Arguments.of("HT-NAME-A", "cyrillic o", "Khach ZZHоney CustomerA can phanh"),
                Arguments.of("HT-NAME-A", "cyrillic capitals", "ZZНoney СustomerА"),
                Arguments.of("HT-NAME-A", "cyrillic everywhere", "ZZНоnеу СuѕtоmеrА"),
                Arguments.of("HT-NAME-A", "greek letters", "ΖΖΗοney CustomerΑ"),
                Arguments.of("HT-NAME-A", "digit zero", "ZZH0ney CustomerA"),
                Arguments.of("HT-NAME-A", "digit stand-ins", "ZZH0n3y Cu5t0m3r4"),
                Arguments.of("HT-NAME-A", "dot", "ZZHoney.CustomerA"),
                Arguments.of("HT-NAME-A", "dashes and underscores", "ZZ-Honey_Customer-A"),
                Arguments.of("HT-NAME-A", "letters spaced", "Z Z H o n e y C u s t o m e r A"),
                Arguments.of("HT-NAME-A", "zero-width and soft hyphen", "ZZH​oney Cus­tomerA"),
                Arguments.of("HT-NAME-A", "accents", "ZZHốney Cústomerà"),
                Arguments.of("HT-NAME-A", "fullwidth", "ＺＺＨｏｎｅｙ ＣｕｓｔｏｍｅｒＡ"),
                Arguments.of("HT-NAME-A", "slash", "Customer ZZHoney/CustomerA"),
                Arguments.of("HT-NAME-A", "base64 of the cyrillic form", "xem " + base64),
                Arguments.of("HT-EMAIL-A", "cyrillic in the email", "hоney.custоmer.a@exаmple.invalid"),
                Arguments.of("HT-EMAIL-A", "capital I for l", "honey.customer.a@example.InvaIid"),
                Arguments.of("HT-EMAIL-A", "dotless i", "honey.customer.a@example.ınvalıd"),
                Arguments.of("HT-KEY-01", "cyrillic a", "key=аpasht_key_abc"),
                Arguments.of("HT-KEY-01", "no underscores", "APASHTKEYabc"),
                Arguments.of("HT-KEY-01", "dashes", "apas-ht-key-abc"),
                // security review (A-55): capitals whose small form is listed, invisible letters, missing look-alikes
                Arguments.of("HT-NAME-A", "cyrillic capital shha", "ZZҺoney CustomerA"),
                Arguments.of("HT-NAME-A", "greek capital lunate sigma", "ZZHoney ϹustomerA"),
                Arguments.of("HT-NAME-A", "greek small eta", "ZZHoηey CustomerA"),
                Arguments.of("HT-NAME-A", "micro sign", "ZZHoney CµstomerA"),
                Arguments.of("HT-NAME-A", "cyrillic small straight u", "ZZHoneү CustomerA"),
                Arguments.of("HT-NAME-A", "armenian o", "ZZHօney CustomerA"),
                Arguments.of("HT-NAME-A", "o with stroke", "ZZHøney CustomerA"),
                Arguments.of("HT-NAME-A", "small capitals", "ᴢᴢʜᴏney ᴄustomerA"),
                Arguments.of("HT-NAME-A", "cherokee capitals", "ᏃᏃᎻoney CustomerA"),
                Arguments.of("HT-NAME-A", "hangul filler", "ZZHoneyㅤCustomerA"),
                Arguments.of("HT-NAME-A", "halfwidth hangul filler", "ZZHoneyﾠCustomerA"),
                Arguments.of("HT-NAME-A", "modifier low macron", "ZZHoneyˍCustomerA"),
                Arguments.of("HT-NAME-A", "modifier apostrophe", "ZZʼHoney CustomerA"),
                Arguments.of("HT-NAME-A", "digit two for z", "22Honey CustomerA"),
                Arguments.of("HT-NAME-A", "plus for t", "ZZHoney Cus+omerA"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("disguisedTextHoneytokens")
    void aDisguisedTextHoneytokenIsDetected(String honeytokenId, String variantId, String text) {
        SensitiveDataDetector.Finding expected = new SensitiveDataDetector.Finding(PolicyCode.HONEYTOKEN, honeytokenId);
        assertThat(detector.detect(text)).as(variantId).contains(expected);
        assertThat(detector.honeytoken(text)).as(variantId).contains(expected);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("disguisedTextHoneytokens")
    void aDisguisedTextHoneytokenInARawPayloadIsDetected(String honeytokenId, String variantId, String text) {
        if (variantId.startsWith("base64")) {
            return; // decoded layers are checked per value, not on the raw payload
        }
        String payload = "{\"messages\":[{\"role\":\"user\",\"text\":\"" + text + "\"}]}";
        assertThat(detector.honeytokenInRawPayload(payload)).as(variantId)
                .contains(new SensitiveDataDetector.Finding(PolicyCode.HONEYTOKEN, honeytokenId));
    }

    /** SEC03-F1 (A-55): folding look-alikes does not turn ordinary Vietnamese or technical text into a token. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "Khách hàng cần má phanh, honey không phải tên",
        "customer A hỏi giá lọc gió",
        "ZZ Honda Customer service",
        "Мой клиент ищет тормозные колодки", // Cyrillic sentence, no token
        "Ο πελάτης θέλει φίλτρο αέρα", // Greek sentence, no token
        "apas hệ thống khóa key",
        "example.invalid là miền thử nghiệm",
        "SKU-BRK-PAD-A 04465-0K290 x2",
        "khach hang can ma phanh truoc cho xe zz honda, giao hang nhanh",
        "Bộ lọc gió động cơ 17801-0Y040 dùng cho Toyota Vios 2014-2022, hàng chính hãng, bảo hành 6 tháng",
        "Má phanh trước (Front brake pad) — Customer service: liên hệ cửa hàng",
        "Ʉ ʼ ˍ ㅤ ﾠ — ký tự lạ nhưng không có token"
    })
    void ordinaryTextWithLookAlikesIsNotAHoneytoken(String text) {
        assertThat(detector.honeytoken(text)).isEmpty();
        assertThat(detector.honeytokenInRawPayload("{\"text\":\"" + text + "\"}")).isEmpty();
    }

    @Test
    void theSkeletonFoldsLookAlikesAndDropsSeparators() {
        assertThat(TextNormalizer.skeleton("ZZHоney CustomerA")).isEqualTo("zzhoneycustomera");
        assertThat(TextNormalizer.skeleton("ΖΖΗ0ney.Cu$tomer-@")).isEqualTo("zzhoneycustomera");
        assertThat(TextNormalizer.skeleton("Đường 1 lIı|")).isEqualTo("duonglllll");
    }

    /** Security review H1 (A-55): a look-alike folds the same in either case. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"а", "с", "е", "һ", "о", "р", "у", "ү", "к", "в", "т", "м", "н", "п", "ϲ", "ο", "α", "ρ",
        "ι", "κ", "ν", "τ", "υ", "χ", "ε", "ω", "ϳ", "η", "γ", "μ", "ø", "ł", "ħ", "ŧ", "đ", "ꮓ", "ꮋ"})
    void aLookAlikeFoldsTheSameInEitherCase(String letter) {
        String upper = letter.toUpperCase(Locale.ROOT);
        String lower = letter.toLowerCase(Locale.ROOT);
        assertThat(TextNormalizer.skeleton(lower)).as("lower").hasSize(1).matches("[a-z]");
        if (DRAWN_DIFFERENTLY_BY_CASE.contains(lower)) {
            assertThat(TextNormalizer.skeleton(upper)).as("upper %s", upper).hasSize(1).matches("[a-z]");
        } else {
            assertThat(TextNormalizer.skeleton(upper)).as("upper %s", upper).isEqualTo(TextNormalizer.skeleton(lower));
        }
    }

    /** Small letters whose capital looks like another Latin letter (Greek eta: "n" / "H"). */
    private static final Set<String> DRAWN_DIFFERENTLY_BY_CASE =
            Set.of("η", "γ", "μ", "ω", "ν", "υ", "в", "т", "м", "н", "п");

    @Test
    void aTextHoneytokenNeedsSixLettersOrDigitsOnceFolded() {
        assertThatThrownBy(() -> new Honeytoken("HT-SHORT", Honeytoken.Kind.TEXT, "a.b.c.d.e"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new Honeytoken("HT-LONG", Honeytoken.Kind.TEXT, "a.b.c.d.e.f").value()).isEqualTo("a.b.c.d.e.f");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"gia 1.250.000đ", "2tr", "tong 450000", "khoang 350k", "$1200", "1,5 triệu",
        "500000 dong", "giá là 450000", "tổng cộng: 1.200.000", "gia von la 1234567"})
    void moneyInCurrencyContextIsDetected(String text) {
        assertThat(detector.detect(text)).map(SensitiveDataDetector.Finding::code).contains(PolicyCode.MONEY);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"0912 345 678", "+84 912345678", "0912.345.678", "024 3826 1234"})
    void phoneNumbersAreDetected(String text) {
        assertThat(detector.detect(text)).map(SensitiveDataDetector.Finding::code).contains(PolicyCode.PHONE);
    }

    @Test
    void emailAndFullVinAreDetected() {
        assertThat(detector.detect("lien he a.b@synthetic.test")).map(SensitiveDataDetector.Finding::code)
                .contains(PolicyCode.EMAIL);
        assertThat(detector.detect("VIN JTDBT923X71234567 nhe")).map(SensitiveDataDetector.Finding::code)
                .contains(PolicyCode.FULL_VIN);
    }

    /** Regression (GW200 run 1): decoded garbage from a trace UUID was flagged as MONEY. */
    @Test
    void identifierShapedValuesDoNotTriggerFindings() {
        Random random = new Random(20261118L);
        for (int i = 0; i < 1000; i++) {
            // Same shape as gateway EgressFixtures.traceId, inlined so policy tests do not depend on gateway.
            String deterministic = new UUID(0x5EC01A0000000000L | i, 0x8000000000000000L | i).toString();
            String randomUuid = new UUID(random.nextLong(), random.nextLong()).toString();
            assertThat(detector.detect(deterministic)).as("trace %d", i).isEmpty();
            assertThat(detector.detect(randomUuid)).as("random uuid %d", i).isEmpty();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "Toi can ma phanh truoc cho Vios 2019, so luong 2",
        "Ma OE 04465-0K290 con hang khong?",
        "SKU-BRK-PAD-A",
        "JTD**************",
        "qh_SyntheticHandleAAAAAAAAAAAA",
        "vin_SyntheticVinRef01",
        "0b6c3a52-4f7e-4c1d-9a8b-2e5f6d7c8b9a",
        "Can 4 banh xe va 2 loc gio, giao truoc 5 gio chieu",
        "dong co 1.5L doi 2018",
        // security review M3 corpus
        "Vios 2019 dong co 1.5",
        "xe gia dinh, doi 2019",
        "tham gia chuong trinh 2024",
        "quoc gia 1234 km",
        "vin_ABCDEFGHJKLMN1234",
        "qh_ABCDEFGHJKLMN1234-ABCDEFGH",
        "{\"type\":\"object\",\"additionalProperties\":false}"
    })
    void ordinaryTechnicalTextPasses(String text) {
        assertThat(detector.detect(text)).isEmpty();
    }

    /** A-39(h): an OE code with letters after a price keyword is a part code, not an amount. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"báo giá 04465-0K290", "Báo giá 04465-0K290 nhé", "giá: 17801-0Y040", "gia 90915-YZZD1",
        "tổng 04465-0k290 và 17801-0Y040", "giá vốn 04152YZZA1", "giá là 04465-0K290?"})
    void anOeCodeAfterAPriceKeywordIsNotMoney(String text) {
        assertThat(detector.detect(text)).isEmpty();
    }

    /** A-39(h) fail-closed side: amounts, digit-only ranges and digit-only codes after a keyword stay MONEY. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"giá 450000-500000", "giá 90915-10004", "giá 450000, mã 04465-0K290", "giá 1.250.000.",
        "tổng 450000-VND", "giá 1.200.000 cho 04465-0K290", "giá là 450000 x 2 bộ",
        // security review of A-39(h): amounts glued to a quantity/currency tail or a model name
        "giá 450000x2", "giá 450000x2 cái", "giá 450.000x2", "giá 450000vnd2", "tổng 1250000vnđx2", "giá 1.250.000đ0",
        "giá 450000Vios2019", "giá 45000x2", "giá 45000X2", "giá 45000k2", "giá 45000-VND", "giá 4465-0K290",
        // second security review: a currency or multiplier word, then "x" or a later digit
        "giá 45000đx2", "giá 45000kx2", "giá 12345usdx2", "giá 45000vndx2", "giá 45000trx2", "giá 45000đồng2",
        "giá 45000dong2", "giá 45000nghìnx2", "giá 45000ngàn2", "GIÁ 45000ĐX2",
        // fail-closed false positives, pinned so a later change cannot loosen them silently
        "giá 04465D1234", "giá 48510-K0010", "giá 90919-D2001"})
    void amountsNextToCodesAreStillMoney(String text) {
        assertThat(detector.detect(text)).map(SensitiveDataDetector.Finding::code).contains(PolicyCode.MONEY);
    }

    /** Second review M2: separators the old pattern did not allow. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"(090) 123 4567", "0901  234  567", "090/123/4567", "090 - 123 - 4567",
        "goi (+84) 90 123 4567 nhe", "(028) 3812 3456"})
    void phoneWithLooserSeparatorsIsDetected(String text) {
        assertThat(detector.detect(text)).map(SensitiveDataDetector.Finding::code).contains(PolicyCode.PHONE);
    }

    /** Second review M3: a full VIN written in groups. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"JTDBT923X7 1234567", "JTDBT-923X7-1234567", "VIN: JTD BT923 X71 234567 nhe",
        "XE JTDBT.923X7.1234567"})
    void splitVinIsDetected(String text) {
        assertThat(detector.detect(text)).map(SensitiveDataDetector.Finding::code).contains(PolicyCode.FULL_VIN);
    }

    /** Split-VIN and loose-phone false-positive corpus: ordinary part numbers, models and dates. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"SKU-BRK-PAD-A", "04465-0K290", "Ma OE 04465-0K290 va 04465-0K291", "Vios 2019 1.5G",
        "Ngay 03/12/2019 can 2 bo", "SKU-BRK-PAD-A x 2, SKU-FLT-AIR-B x 1", "Innova 2020 2.0E MT"})
    void separatedCodesAreNotVinsOrPhones(String text) {
        assertThat(detector.detect(text)).isEmpty();
    }

    /** Second review M1: junk byte and misaligned base64 no longer hide the encoded value. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"V27-base64-junk-byte", "V28-hex-junk-byte", "V29-base64-prefixed"})
    void encodedSentinelWithJunkOrPrefixIsDetected(String variant) {
        assertThat(detector.detect(SentinelVariants.of(SentinelVariants.COST).get(variant)))
                .map(SensitiveDataDetector.Finding::honeytokenId).contains("HT-COST-01");
    }

    @Test
    void encodedPhoneWithJunkByteIsDetected() {
        String encoded = Base64.getEncoder().encodeToString(("0912345678" + (char) 1).getBytes(StandardCharsets.UTF_8));
        assertThat(detector.detect(encoded)).map(SensitiveDataDetector.Finding::code).contains(PolicyCode.PHONE);
        assertThat(detector.detect("x" + Base64.getEncoder()
                .encodeToString("0912345678".getBytes(StandardCharsets.UTF_8))))
                .map(SensitiveDataDetector.Finding::code).contains(PolicyCode.PHONE);
    }

    /** Third review M-A/M-B: suffix junk on unpadded base64 and a hex digit in front. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"V30-base64-suffixed", "V31-base64-both-sides", "V32-hex-prefixed"})
    void encodedSentinelWithGluedCharactersIsDetected(String variant) {
        assertThat(detector.detect(SentinelVariants.of(SentinelVariants.COST).get(variant)))
                .map(SensitiveDataDetector.Finding::honeytokenId).contains("HT-COST-01");
    }

    @Test
    void encodedPhoneWithGluedCharactersIsDetected() {
        String unpadded = Base64.getEncoder().withoutPadding()
                .encodeToString("0912345678".getBytes(StandardCharsets.UTF_8));
        for (String text : List.of(unpadded + "xyz", unpadded + "x", "x" + unpadded + "x",
                "a" + HexFormat.of().formatHex("0912345678".getBytes(StandardCharsets.US_ASCII)))) {
            assertThat(detector.detect(text)).as(text).map(SensitiveDataDetector.Finding::code)
                    .contains(PolicyCode.PHONE);
        }
    }

    /** Third review M-C: a full VIN next to a dash in free text. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"VIN-1HGCM82633A004352", "1HGCM82633A004352-01", "so khung: -1HGCM82633A004352-"})
    void fullVinNextToADashIsDetected(String text) {
        assertThat(detector.detect(text)).map(SensitiveDataDetector.Finding::code).contains(PolicyCode.FULL_VIN);
    }

    /** Third review L-3: too many encoded-looking runs is an error, never a silent partial scan. */
    @Test
    void tooManyEncodedRunsIsRejected() {
        String text = String.join(" ", Collections.nCopies(OneLayerDecoder.MAX_CANDIDATES + 1, "QUJDREVGR0g="));
        assertThatThrownBy(() -> detector.detect(text)).isInstanceOf(OneLayerDecoder.TooManyCandidatesException.class);
    }
}
