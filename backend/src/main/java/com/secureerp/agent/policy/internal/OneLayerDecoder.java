package com.secureerp.agent.policy.internal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decodes one layer of the encodings named in AI_GATEWAY §2.3: base64 (standard and URL-safe), hex,
 * percent-encoding, {@code \\uXXXX} escapes and HTML numeric entities. Bounded: a text with more
 * than {@link #MAX_CANDIDATES} encoded-looking runs is rejected ({@link TooManyCandidatesException}),
 * so hostile input can neither make the scan expensive nor push a value past the limit unscanned.
 */
public final class OneLayerDecoder {

    static final int MAX_CANDIDATES = 256;

    private static final Pattern BASE64 = Pattern.compile("[A-Za-z0-9+/_-]{8,}={0,2}");
    private static final Pattern HEX = Pattern.compile("(?:0x)?([0-9a-fA-F]{8,})");
    private static final Pattern ESCAPED_HEX = Pattern.compile("(?:\\\\x[0-9a-fA-F]{2}){4,}");
    private static final Pattern PERCENT = Pattern.compile("(?:%[0-9a-fA-F]{2})+");
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
    private static final Pattern HTML_DECIMAL = Pattern.compile("&#(\\d{1,7});?");
    private static final Pattern HTML_HEX = Pattern.compile("&#[xX]([0-9a-fA-F]{1,6});?");

    private OneLayerDecoder() {
    }

    /**
     * Thrown when a text holds more encoded-looking runs than {@link #MAX_CANDIDATES}. The scan
     * would otherwise stop decoding silently (third review L-3); callers treat it as a block.
     */
    public static final class TooManyCandidatesException extends RuntimeException {

        TooManyCandidatesException() {
            super("too many encoded candidates");
        }
    }

    private static int countCandidate(int candidates) {
        if (candidates >= MAX_CANDIDATES) {
            throw new TooManyCandidatesException();
        }
        return candidates + 1;
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
        while (base64.find()) {
            candidates = countCandidate(candidates);
            // Characters glued in front or behind shift the 4-character alignment (second review M1,
            // third review M-A), so every start offset 0..3 and every end trim 0..3 is decoded.
            String run = base64.group().replace("=", "");
            for (int offset = 0; offset < 4; offset++) {
                for (int trim = 0; trim < 4; trim++) {
                    int end = run.length() - trim;
                    if (end - offset >= 8) {
                        decodeBase64(run.substring(offset, end)).flatMap(OneLayerDecoder::scannableText)
                                .ifPresent(variants::add);
                    }
                }
            }
        }
        Matcher hex = HEX.matcher(text);
        while (hex.find()) {
            candidates = countCandidate(candidates);
            // One hex digit glued in front shifts the byte pairs (third review M-B): decode at 0 and 1.
            String run = hex.group(1);
            for (int offset = 0; offset < 2; offset++) {
                int end = run.length() - (run.length() - offset) % 2;
                if (end - offset >= 8) {
                    scannableText(utf8(HexFormat.of().parseHex(run, offset, end))).ifPresent(variants::add);
                }
            }
        }
        Matcher escapedHex = ESCAPED_HEX.matcher(text);
        while (escapedHex.find()) {
            candidates = countCandidate(candidates);
            scannableText(utf8(HexFormat.of().parseHex(escapedHex.group().replace("\\x", ""))))
                    .ifPresent(variants::add);
        }
        return variants;
    }

    /** Above this share of undecodable or control characters a decoded run counts as binary. */
    static final double MAX_BINARY_SHARE = 0.25;

    /**
     * The decoded run with undecodable bytes and control characters replaced by spaces, if it is
     * mostly text. Random identifiers (UUIDs, handles) that look like base64/hex decode to binary
     * garbage, which otherwise produces false pattern hits, so mostly-binary runs are dropped. A
     * single junk byte added to an encoded value no longer hides it (second review M1: before, one
     * such byte discarded the whole run).
     */
    static Optional<String> scannableText(String decoded) {
        int[] codePoints = decoded.codePoints().toArray();
        if (codePoints.length < 4) {
            return Optional.empty();
        }
        StringBuilder cleaned = new StringBuilder(decoded.length());
        int binary = 0;
        for (int cp : codePoints) {
            if (cp == 0xFFFD || (Character.isISOControl(cp) && cp != '\n' && cp != '\t' && cp != '\r')) {
                binary++;
                cleaned.append(' ');
            } else {
                cleaned.appendCodePoint(cp);
            }
        }
        if (binary > codePoints.length * MAX_BINARY_SHARE || cleaned.toString().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(cleaned.toString());
    }

    private static Optional<String> decodeBase64(String candidate) {
        String body = candidate.replace("=", ""); // java.util.Base64 accepts unpadded input
        if (body.length() % 4 == 1) {
            return Optional.empty();
        }
        for (Base64.Decoder decoder : List.of(Base64.getDecoder(), Base64.getUrlDecoder())) {
            try {
                return Optional.of(utf8(decoder.decode(body)));
            } catch (IllegalArgumentException ignored) {
                // try the other alphabet
            }
        }
        return Optional.empty();
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
