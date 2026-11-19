package com.secureerp.agent.identity.spi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An authenticated channel user (ERP_CONNECTOR §3.1, A-37 APPROVED 2026-11-19): the channel and the
 * SHA-256 of the provider's user ID. The user ID itself is never stored or passed on. Only identity
 * builds one from an authenticated login; the ERP link lookup takes nothing else (no name, phone or
 * prefix search, rule 10).
 *
 * <p>DESIGN PROPOSAL (EP-04): an unkeyed hash with a fixed domain prefix. It pseudonymises, it does not
 * anonymise: whoever knows a user ID can compute the hash (fine for synthetic demo users; a keyed hash
 * is needed before real channel IDs, D-06).
 */
public record ChannelIdentity(String channel, String providerUserHash) {

    private static final Pattern CHANNEL = Pattern.compile("^[A-Z_]{1,20}$");
    private static final Pattern HASH = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern USER_ID = Pattern.compile("^[a-z0-9._-]{3,64}$");

    public ChannelIdentity {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(providerUserHash, "providerUserHash");
        if (!CHANNEL.matcher(channel).matches()) {
            throw new IllegalArgumentException("invalid channel format");
        }
        if (!HASH.matcher(providerUserHash).matches()) {
            throw new IllegalArgumentException("invalid provider user hash format");
        }
    }

    /** Hashes an authenticated provider user ID (lower-cased) for one channel. */
    public static ChannelIdentity of(String channel, String providerUserId) {
        Objects.requireNonNull(providerUserId, "providerUserId");
        String normalized = providerUserId.toLowerCase(Locale.ROOT);
        if (!USER_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("invalid provider user id format");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("apas:channel-user:" + channel + ":" + normalized).getBytes(StandardCharsets.UTF_8));
            return new ChannelIdentity(channel, HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Override
    public String toString() {
        return "ChannelIdentity[" + channel + ", redacted]";
    }
}
