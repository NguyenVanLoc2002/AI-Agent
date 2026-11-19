package com.secureerp.agent.quote.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Random server tokens (ADR-008): the quote handle ({@code qh_} + 128 bits, base64url) and the
 * public quote reference shown on the card ({@code BG-} + 8 characters without look-alikes).
 */
final class QuoteTokens {

    static final String HANDLE_PREFIX = "qh_";
    static final Pattern HANDLE = Pattern.compile("^qh_[A-Za-z0-9_-]{22}$");
    /** EP-07: the confirmation token of a rendered card ({@code qc_} + 128 bits); only its SHA-256 is stored. */
    static final String CONFIRMATION_PREFIX = "qc_";
    static final Pattern CONFIRMATION = Pattern.compile("^qc_[A-Za-z0-9_-]{22}$");
    static final Pattern PUBLIC_REF = Pattern.compile("^BG-[A-HJ-NP-Z2-9]{8}$");
    /** Egress rule for opaque refs: no run of 7+ digits, also across - and _ (A-26). */
    private static final Pattern DIGIT_RUN = Pattern.compile("\\d{7,}");
    private static final String REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    /**
     * Same rule as the tool argument check (T-TOOL-13): 17 VIN characters with a digit and a letter,
     * case-insensitive, also across - and _ and the prefix. The model will pass the handle back as an
     * argument (EP-07), so a handle must never look like a VIN.
     */
    private static final Pattern VIN_LIKE = Pattern.compile(
            "(?=[A-HJ-NPR-Z0-9]{0,16}\\d)(?=[A-HJ-NPR-Z0-9]{0,16}[A-HJ-NPR-Z])[A-HJ-NPR-Z0-9]{17}");
    /** About 26 % of random handles contain a VIN-like run; 64 draws make a failure negligible. */
    private static final int MAX_DRAWS = 64;

    private final SecureRandom random = new SecureRandom();

    String newHandle() {
        byte[] bytes = new byte[16];
        for (int draw = 0; draw < MAX_DRAWS; draw++) {
            random.nextBytes(bytes);
            String token = HANDLE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            String joined = token.replace("-", "").replace("_", "");
            String upper = token.toUpperCase(Locale.ROOT);
            if (!DIGIT_RUN.matcher(joined).find() && !VIN_LIKE.matcher(upper).find()
                    && !VIN_LIKE.matcher(joined.toUpperCase(Locale.ROOT)).find()) {
                return token;
            }
        }
        throw new IllegalStateException("could not draw a quote handle");
    }

    /** Shown to the authenticated customer only, never to the model, so it needs no VIN or digit-run redraw. */
    String newConfirmationToken() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return CONFIRMATION_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String newPublicRef() {
        StringBuilder ref = new StringBuilder("BG-");
        for (int i = 0; i < 8; i++) {
            ref.append(REF_ALPHABET.charAt(random.nextInt(REF_ALPHABET.length())));
        }
        return ref.toString();
    }

    static String hash(String handle) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(handle.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
