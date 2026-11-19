package com.secureerp.agent.policy.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.policy.InboundSanitizer.Blocked;
import com.secureerp.agent.policy.InboundSanitizer.RedactionFlag;
import com.secureerp.agent.policy.InboundSanitizer.Sanitized;
import com.secureerp.agent.policy.InboundSanitizer;
import com.secureerp.agent.policy.PolicyCode;
import com.secureerp.agent.policy.SentinelVariants;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T-SEC01-USERMONEY and T-SEC01-VIN (unit), per A-10 / A-11 APPROVED 2026-11-18. */
class DefaultInboundSanitizerTest {

    private final InboundSanitizer sanitizer = new DefaultInboundSanitizer(new SensitiveDataDetector());

    @Test
    void sentinelTypedByCustomerIsBlocked() {
        assertThat(sanitizer.sanitize("gia von la " + SentinelVariants.COST + " phai khong"))
                .isEqualTo(new Blocked(PolicyCode.HONEYTOKEN, "HT-COST-01"));
        assertThat(sanitizer.sanitize("cong no " + SentinelVariants.of(SentinelVariants.CREDIT).get("V02-dots")))
                .isEqualTo(new Blocked(PolicyCode.HONEYTOKEN, "HT-CREDIT-01"));
    }

    @Test
    void ordinaryPriceIsRedactedAndFlagged() {
        Sanitized result = (Sanitized) sanitizer.sanitize("Hom qua bao gia 1.250.000đ, gio con gia 1.200.000 khong?");
        assertThat(result.text()).doesNotContain("1.250.000").doesNotContain("1.200.000").contains("[SO_TIEN]");
        assertThat(result.flags()).containsExactly(RedactionFlag.MONEY);
    }

    @Test
    void phoneAndEmailAreRedacted() {
        Sanitized result = (Sanitized) sanitizer.sanitize("Goi 0912 345 678 hoac mail a.b@synthetic.test");
        assertThat(result.text()).doesNotContain("345").doesNotContain("@").contains("[SDT]").contains("[EMAIL]");
        assertThat(result.flags()).containsExactlyInAnyOrder(RedactionFlag.PHONE, RedactionFlag.EMAIL);
    }

    @Test
    void fullVinIsReducedToWmi() {
        Sanitized result = (Sanitized) sanitizer.sanitize("VIN xe toi la jtdbt923x71234567 nhe");
        assertThat(result.text()).contains("JTD**************").doesNotContainIgnoringCase("923X71234567");
        assertThat(result.flags()).containsExactly(RedactionFlag.VIN_MASKED);
    }

    @Test
    void technicalTextIsUnchanged() {
        String text = "Can ma phanh OE 04465-0K290 cho Vios 2019, so luong 2";
        assertThat(sanitizer.sanitize(text)).isEqualTo(new Sanitized(text, Set.of()));
    }

    /** A-39(h): "báo giá <OE>" keeps the OE, so the model can search it. */
    @Test
    void anOeCodeAfterGiaIsKept() {
        String text = "Báo giá 2 bộ má phanh, báo giá 04465-0K290 luôn";
        assertThat(sanitizer.sanitize(text)).isEqualTo(new Sanitized(text, Set.of()));
        Sanitized mixed = (Sanitized) sanitizer.sanitize("giá 1.200.000 cho 04465-0K290, giá 90915-10004");
        // A digit-only code after "giá" is still read as an amount (unchanged, fail-closed; A-39(h) residual).
        assertThat(mixed.text()).isEqualTo("[SO_TIEN] cho 04465-0K290, [SO_TIEN]-10004");
        assertThat(mixed.flags()).containsExactly(RedactionFlag.MONEY);
    }

    /** A-39(h), second security review: the sanitizer (case-insensitive copy) agrees with the detector. */
    @Test
    void amountsGluedToCurrencyWordsAreRedacted() {
        for (String amount : List.of("45000đx2", "45000kx2", "45000vndx2", "45000đồng2", "45000ĐX2")) {
            Sanitized result = (Sanitized) sanitizer.sanitize("GIÁ " + amount + " nhé");
            assertThat(result.text()).as(amount).doesNotContain("45000").contains("[SO_TIEN]");
        }
    }

    /** A-39(h): a sentinel glued to a code-like tail is still a honeytoken. */
    @Test
    void aSentinelWithACodeTailIsStillBlocked() {
        assertThat(sanitizer.sanitize("giá " + SentinelVariants.COST + "-0K290"))
                .isEqualTo(new Blocked(PolicyCode.HONEYTOKEN, "HT-COST-01"));
    }

    @Test
    void obfuscatedPhoneThatSurvivesRedactionIsBlocked() {
        String base64Phone = Base64.getEncoder().encodeToString("0912345678".getBytes());
        assertThat(sanitizer.sanitize("so cua toi " + base64Phone)).isEqualTo(new Blocked(PolicyCode.PHONE, null));
    }

    /** Second review M3: a VIN typed in groups is masked to WMI + 14 asterisks, like a plain one. */
    @Test
    void splitVinIsMasked() {
        InboundSanitizer.Result result = sanitizer.sanitize("Xe toi VIN JTDBT-923X7-1234567, can ma phanh");
        assertThat(result).isInstanceOf(Sanitized.class);
        Sanitized sanitized = (Sanitized) result;
        assertThat(sanitized.text()).contains("JTD**************").doesNotContain("1234567");
        assertThat(sanitized.flags()).contains(RedactionFlag.VIN_MASKED);
    }

    /** Second review M2: phone formats with brackets, slashes and double spaces are redacted. */
    @Test
    void phoneWithLooserSeparatorsIsRedacted() {
        InboundSanitizer.Result result = sanitizer.sanitize("goi (090) 123 4567 hoac 090/123/4567 nhe");
        assertThat(result).isInstanceOf(Sanitized.class);
        assertThat(((Sanitized) result).text()).doesNotContain("4567").contains("[SDT]");
    }

    /** Third review M-C: a full VIN next to a dash is masked. */
    @Test
    void fullVinNextToADashIsMasked() {
        InboundSanitizer.Result result = sanitizer.sanitize("VIN-1HGCM82633A004352 can loc gio");
        assertThat(result).isInstanceOf(Sanitized.class);
        assertThat(((Sanitized) result).text()).contains("1HG**************").doesNotContain("004352");
    }

    /** Third review L-3: too many encoded runs block instead of scanning part of the text. */
    @Test
    void tooManyEncodedRunsAreBlocked() {
        String text = String.join(" ", Collections.nCopies(300, "QUJDREVGR0g="));
        assertThat(sanitizer.sanitize(text)).isEqualTo(new Blocked(PolicyCode.MALFORMED_PAYLOAD, null));
    }
}
