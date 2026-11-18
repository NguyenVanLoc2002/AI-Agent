package com.secureerp.agent.policy.internal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decodes one layer of the encodings named in AI_GATEWAY §2.3: base64 (standard and URL-safe), hex,
 * percent-encoding, {@code \\uXXXX} escapes and HTML numeric entities. Bounded: at most
 * {@link #MAX_CANDIDATES} candidates per text, so hostile input cannot make the scan expensive.
 */
public final class OneLayerDecoder {

    static final int MAX_CANDIDATES = 256;

    private static final Pattern BASE64 = Pattern.compile("[A-Za-z0-9+/_-]{8,}={0,2}");
    private static final Pattern HEX = Pattern.compile("(?:0x)?((?:[0-9a-fA-F]{2}){4,})");
    private static final Pattern ESCAPED_HEX = Pattern.compile("(?:\\\\x[0-9a-fA-F]{2}){4,}");
    private static final Pattern PERCENT = Pattern.compile("(?:%[0-9a-fA-F]{2})+");
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
    private static final Pattern HTML_DECIMAL = Pattern.compile("&#(\\d{1,7});?");
    private static final Pattern HTML_HEX = Pattern.compile("&#[xX]([0-9a-fA-F]{1,6});?");

    private OneLayerDecoder() {
    }

    /**
     * Returns decoded variants of {@code text}: one with in-place decodings (percent, unicode
     * escapes, HTML entities) and one per decodable base64/hex run. The original is not included.
     */
    public static List<String> decodedVariants(String text) {
        List<String> variants = new ArrayList<>();
        String inPlace = replaceHtml(replaceUnicodeEscapes(replacePercent(text)));
        if (!inPlace.equals(text)) {
            variants.add(inPlace);
        }
        int candidates = 0;
        Matcher base64 = BASE64.matcher(text);
        while (base64.find() && candidates++ < MAX_CANDIDATES) {
            decodeBase64(base64.group()).filter(OneLayerDecoder::plausibleText).ifPresent(variants::add);
        }
        Matcher hex = HEX.matcher(text);
        while (hex.find() && candidates++ < MAX_CANDIDATES) {
            addIfPlausible(variants, utf8(HexFormat.of().parseHex(hex.group(1))));
        }
        Matcher escapedHex = ESCAPED_HEX.matcher(text);
        while (escapedHex.find() && candidates++ < MAX_CANDIDATES) {
            addIfPlausible(variants, utf8(HexFormat.of().parseHex(escapedHex.group().replace("\\x", ""))));
        }
        return variants;
    }

    private static void addIfPlausible(List<String> variants, String decoded) {
        if (plausibleText(decoded)) {
            variants.add(decoded);
        }
    }

    /**
     * Only decoded runs that are valid UTF-8 text without control characters are scanned. Random
     * identifiers (UUIDs, handles) that happen to look like base64/hex decode to binary garbage,
     * which otherwise produces false pattern hits. A real encoded value always decodes to text.
     */
    static boolean plausibleText(String decoded) {
        if (decoded.length() < 4) {
            return false;
        }
        return decoded.codePoints().noneMatch(cp -> cp == 0xFFFD
                || (Character.isISOControl(cp) && cp != '\n' && cp != '\t' && cp != '\r'));
    }

    private static java.util.Optional<String> decodeBase64(String candidate) {
        String body = candidate.replace("=", ""); // java.util.Base64 accepts unpadded input
        if (body.length() % 4 == 1) {
            return java.util.Optional.empty();
        }
        for (Base64.Decoder decoder : List.of(Base64.getDecoder(), Base64.getUrlDecoder())) {
            try {
                return java.util.Optional.of(utf8(decoder.decode(body)));
            } catch (IllegalArgumentException ignored) {
                // try the other alphabet
            }
        }
        return java.util.Optional.empty();
    }

    private static String replacePercent(String text) {
        Matcher m = PERCENT.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            String run = m.group();
            for (int i = 0; i < run.length(); i += 3) {
                bytes.write(Integer.parseInt(run.substring(i + 1, i + 3), 16));
            }
            m.appendReplacement(out, Matcher.quoteReplacement(utf8(bytes.toByteArray())));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String replaceUnicodeEscapes(String text) {
        return UNICODE_ESCAPE.matcher(text).replaceAll(r -> Matcher.quoteReplacement(
                String.valueOf((char) Integer.parseInt(r.group(1), 16))));
    }

    private static String replaceHtml(String text) {
        String decimal = HTML_DECIMAL.matcher(text).replaceAll(r -> codePoint(Integer.parseInt(r.group(1))));
        return HTML_HEX.matcher(decimal).replaceAll(r -> codePoint(Integer.parseInt(r.group(1), 16)));
    }

    private static String codePoint(int cp) {
        return Character.isValidCodePoint(cp) ? Matcher.quoteReplacement(new String(Character.toChars(cp))) : "";
    }

    private static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
