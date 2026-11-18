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
import java.util.Map;
import java.util.Random;
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

    @Test
    void textHoneytokensAreDetected() {
        assertThat(detector.detect("Khach ZZHONEY   customerA can phanh")).map(SensitiveDataDetector.Finding::honeytokenId)
                .contains("HT-NAME-A");
        assertThat(detector.detect("key=APASHT_KEY_abc")).map(SensitiveDataDetector.Finding::honeytokenId)
                .contains("HT-KEY-01");
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
