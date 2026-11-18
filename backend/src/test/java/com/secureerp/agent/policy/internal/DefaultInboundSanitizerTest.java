package com.secureerp.agent.policy.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.policy.InboundSanitizer;
import com.secureerp.agent.policy.InboundSanitizer.Blocked;
import com.secureerp.agent.policy.InboundSanitizer.RedactionFlag;
import com.secureerp.agent.policy.InboundSanitizer.Sanitized;
import com.secureerp.agent.policy.PolicyCode;
import com.secureerp.agent.policy.SentinelVariants;
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
        assertThat(sanitizer.sanitize(text)).isEqualTo(new Sanitized(text, java.util.Set.of()));
    }

    @Test
    void obfuscatedPhoneThatSurvivesRedactionIsBlocked() {
        String base64Phone = java.util.Base64.getEncoder().encodeToString("0912345678".getBytes());
        assertThat(sanitizer.sanitize("so cua toi " + base64Phone)).isEqualTo(new Blocked(PolicyCode.PHONE, null));
    }
}
