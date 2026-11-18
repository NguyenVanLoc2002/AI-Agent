package com.secureerp.agent.catalog.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T-UC02-VIN, validator part (BR-04; check-digit rule: owner decision 2026-11-18). Synthetic VINs
 * only; their check digits were computed with ISO 3779 weights.
 */
class VinTest {

    /** North America (first character 1–5): the check digit is required. */
    private static final String NA_VALID = "5ZZAPAS11RA000001";
    private static final String NA_CHECK_X = "2ZZAPAS1XRA000001";
    /** Not North America: format only, any check digit. */
    private static final String ASIA_ANY_CHECK = "RLZAPAS20RA000002";

    @ParameterizedTest
    @ValueSource(strings = {NA_VALID, NA_CHECK_X, "3ZZAPAS15RB000010", "4ZZAPAS14RB000011", ASIA_ANY_CHECK})
    void validVinsAreAccepted(String vin) {
        assertThat(Vin.isValid(vin)).isTrue();
        assertThat(new Vin(vin).value()).isEqualTo(vin);
    }

    @Test
    void lowerCaseAndSurroundingSpaceAreNormalised() {
        assertThat(new Vin("  5zzapas11ra000001 ").value()).isEqualTo(NA_VALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "5ZZAPAS10RA000001", // wrong check digit (1 expected)
        "5ZZAPAS1XRA000001", // X where a digit is expected
        "2ZZAPAS10RA000001" // X expected
    })
    void northAmericanVinNeedsTheRightCheckDigit(String vin) {
        assertThat(Vin.isValid(vin)).isFalse();
        assertThatThrownBy(() -> new Vin(vin)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid vin check digit");
    }

    @Test
    void otherRegionsAreCheckedForFormatOnly() {
        assertThat(Vin.checkDigit(ASIA_ANY_CHECK)).isNotEqualTo(ASIA_ANY_CHECK.charAt(8));
        assertThat(Vin.isValid(ASIA_ANY_CHECK)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "5ZZAPAS11RA00000", // 16
        "5ZZAPAS11RA0000012", // 18
        "5ZZAPAS11RA00000I", // I
        "5ZZAPAS11RA00000O", // O
        "RLZAPAS20RA00000Q", // Q
        "5ZZ-APAS11RA00000", // separator
        "5ZZAPAS11RA00000*", // symbol
        "",
        "                 "
    })
    void malformedValuesAreRejected(String vin) {
        assertThat(Vin.isValid(vin)).isFalse();
        assertThatThrownBy(() -> new Vin(vin)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullIsNotAVin() {
        assertThat(Vin.isValid(null)).isFalse();
    }

    /** BR-30, A-11: the only printable forms are WMI + mask; a log line cannot carry the VIN. */
    @Test
    void toStringAndMaskedNeverRevealTheVin() {
        Vin vin = new Vin(NA_VALID);
        assertThat(vin.wmi()).isEqualTo("5ZZ");
        assertThat(vin.masked()).isEqualTo("5ZZ**************");
        assertThat(vin.toString()).doesNotContain(NA_VALID).contains("5ZZ**************");
        assertThat(new ExactProductQuery.ByVin(vin).toString()).doesNotContain(NA_VALID);
    }
}
