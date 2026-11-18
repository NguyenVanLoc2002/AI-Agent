package com.secureerp.agent.policy.internal;

import com.secureerp.agent.policy.InboundSanitizer;
import com.secureerp.agent.policy.PolicyCode;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * A-10 / A-11 (APPROVED 2026-11-18): honeytoken → block; money, phone, email → placeholder; full VIN
 * → WMI + 14 asterisks; anything still detected after redaction → block (fail-closed).
 */
@Component
public class DefaultInboundSanitizer implements InboundSanitizer {

    static final String MONEY_PLACEHOLDER = "[SO_TIEN]";
    static final String PHONE_PLACEHOLDER = "[SDT]";
    static final String EMAIL_PLACEHOLDER = "[EMAIL]";
    static final String VIN_MASK = "**************";

    private final SensitiveDataDetector detector;

    public DefaultInboundSanitizer(SensitiveDataDetector detector) {
        this.detector = detector;
    }

    @Override
    public Result sanitize(String customerText) {
        Optional<SensitiveDataDetector.Finding> honeytoken = detector.honeytoken(customerText);
        if (honeytoken.isPresent()) {
            return new Blocked(PolicyCode.HONEYTOKEN, honeytoken.get().honeytokenId());
        }
        Set<RedactionFlag> flags = EnumSet.noneOf(RedactionFlag.class);
        String text = TextNormalizer.canonical(customerText);
        text = replace(text, SensitiveDataDetector.EMAIL, EMAIL_PLACEHOLDER, RedactionFlag.EMAIL, flags);
        text = replace(text, caseInsensitive(SensitiveDataDetector.PHONE), PHONE_PLACEHOLDER, RedactionFlag.PHONE, flags);
        text = replace(text, caseInsensitive(SensitiveDataDetector.MONEY_SUFFIX), MONEY_PLACEHOLDER, RedactionFlag.MONEY, flags);
        text = replace(text, caseInsensitive(SensitiveDataDetector.MONEY_PREFIX), MONEY_PLACEHOLDER, RedactionFlag.MONEY, flags);
        Matcher vin = SensitiveDataDetector.FULL_VIN.matcher(text);
        if (vin.find()) {
            flags.add(RedactionFlag.VIN_MASKED);
            text = vin.replaceAll(m -> Matcher.quoteReplacement(m.group().substring(0, 3).toUpperCase() + VIN_MASK));
        }
        Optional<SensitiveDataDetector.Finding> residual = detector.detect(text);
        if (residual.isPresent()) {
            return new Blocked(residual.get().code(), residual.get().honeytokenId());
        }
        return new Sanitized(text, flags);
    }

    private static Pattern caseInsensitive(Pattern pattern) {
        return Pattern.compile(pattern.pattern(), pattern.flags() | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static String replace(String text, Pattern pattern, String placeholder, RedactionFlag flag,
            Set<RedactionFlag> flags) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) {
            return text;
        }
        flags.add(flag);
        return m.replaceAll(Matcher.quoteReplacement(placeholder));
    }
}
