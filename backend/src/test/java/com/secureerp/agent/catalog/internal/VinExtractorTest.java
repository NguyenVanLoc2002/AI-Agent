package com.secureerp.agent.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.catalog.VinIntake;
import com.secureerp.agent.catalog.spi.Vin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** T-UC02-VIN, intake part (UC-02 AC-02.1): which messages store a VIN and which ask again. */
class VinExtractorTest {

    private static final String VIN = "5ZZAPAS11RA000001";
    private static final String OTHER_VIN = "RLZAPAS20RA000002";

    @ParameterizedTest
    @ValueSource(strings = {
        "So khung xe toi la 5ZZAPAS11RA000001, can ma phanh",
        "vin: 5zzapas11ra000001",
        "5ZZAP-AS11R-A000001",
        "VIN 5ZZAPAS11RA000001 va lai 5ZZAPAS11RA000001"
    })
    void oneValidVinIsAccepted(String message) {
        VinExtractor.Extraction extraction = VinExtractor.extract(message);

        assertThat(extraction.intake()).isEqualTo(new VinIntake.Accepted("5ZZ**************"));
        assertThat(extraction.vin()).contains(new Vin(VIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Can ma phanh truoc cho Vios 2019",
        "OE 04465-0K290 con hang khong?",
        "ma 90915-YZZD4-ABCDE", // dashed, not 17 characters: an OE-like code, not a VIN
        "so luong 1234567890123456", // digits only
        "abcdefghijklmnopq", // letters only
        ""
    })
    void messagesWithoutAVinLeaveTheStoredVinAlone(String message) {
        assertThat(VinExtractor.extract(message).intake()).isEqualTo(new VinIntake.None());
    }

    @Test
    void nullMessageHasNoVin() {
        assertThat(VinExtractor.extract(null).intake()).isEqualTo(new VinIntake.None());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "VIN 5ZZAPAS10RA000001", // check digit
        "VIN 5ZZAPAS11RA00000", // 16 characters
        "VIN 5ZZAPAS11RA0000012", // 18 characters
        "VIN 5ZZAPAS11RA00000O", // letter O
        "VIN RLZAPAS20RA00000I", // letter I
        "VIN 5ZZAP-AS10R-A000001", // dashed, wrong check digit
        "VIN 5ZZAPAS11RA000001 hoac 5ZZAPAS10RA000001" // one valid, one not
    })
    void aVinLikeValueThatFailsBr04AsksAgain(String message) {
        VinExtractor.Extraction extraction = VinExtractor.extract(message);

        assertThat(extraction.intake()).isEqualTo(new VinIntake.Invalid());
        assertThat(extraction.vin()).isEmpty();
    }

    @Test
    void twoDifferentValidVinsAskWhichVehicle() {
        VinExtractor.Extraction extraction = VinExtractor.extract("xe 1: " + VIN + ", xe 2: " + OTHER_VIN);

        assertThat(extraction.intake()).isEqualTo(new VinIntake.Multiple());
        assertThat(extraction.vin()).isEmpty();
    }

    @Test
    void tooManyDistinctVinLikeValuesAreRejected() {
        StringBuilder message = new StringBuilder();
        for (int i = 0; i <= VinExtractor.MAX_CANDIDATES; i++) {
            message.append("5ZZAPAS11RA00000").append(i).append(' ');
        }

        assertThat(VinExtractor.extract(message.toString()).intake()).isEqualTo(new VinIntake.Invalid());
        // The same VIN repeated is one value.
        assertThat(VinExtractor.extract((VIN + " ").repeat(10)).intake())
                .isEqualTo(new VinIntake.Accepted("5ZZ**************"));
    }

    /**
     * Parity with the inbound sanitizer's split-VIN rule (EP-05 reviews: security M-1, architecture
     * M3): groups separated by spaces, dots or dashes are read as a VIN, so a new vehicle cannot be
     * masked for the model while the old VIN stays current. Cross-module parity: VinSearchIT.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "so khung 5ZZAP AS11R A000001",
        "so khung 5ZZAP.AS11R.A000001",
        "so khung 5ZZ APAS1 1RA000001",
        "so khung 5Z-ZA-PA-S1-1R-A0-00001", // more than 5 groups
        "so khung 5ZZAP  AS11R. A000001" // two separators
    })
    void vinsWrittenInGroupsAreRead(String message) {
        assertThat(VinExtractor.extract(message).intake()).isEqualTo(new VinIntake.Accepted("5ZZ**************"));
    }

    @Test
    void aGroupedVinNextToAPlainOneIsAmbiguous() {
        assertThat(VinExtractor.extract(VIN + " va RLZAP AS20R A000002").intake()).isEqualTo(new VinIntake.Multiple());
    }

    /**
     * A-32 (DESIGN PROPOSAL, recorded): a long code without dashes that looks like a VIN is read as a
     * VIN typo and the customer is asked again. Fail closed; the business impact is OPEN.
     */
    @ParameterizedTest
    @ValueSource(strings = {"OE 90915YZZD4ABCDE1", "don hang DH2026111800001A"})
    void longCodesWithoutDashesAreTreatedAsVinTypos(String message) {
        assertThat(VinExtractor.extract(message).intake()).isEqualTo(new VinIntake.Invalid());
    }

    @Test
    void longMessagesAreScannedInLinearTime() {
        assertThat(VinExtractor.extract("a1".repeat(50_000)).intake()).isEqualTo(new VinIntake.None());
        assertThat(VinExtractor.extract("a1-".repeat(30_000)).intake()).isEqualTo(new VinIntake.None());
        assertThat(VinExtractor.extract("a1".repeat(50_000) + " " + VIN).intake())
                .isEqualTo(new VinIntake.Accepted("5ZZ**************"));
        assertThat(VinExtractor.extract("x ".repeat(50_000) + VIN).intake())
                .isEqualTo(new VinIntake.Accepted("5ZZ**************"));
    }
}
