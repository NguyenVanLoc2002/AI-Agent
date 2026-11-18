package com.secureerp.agent.platform;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Server-generated conversation identifier: {@code cv_} plus 22 base64url characters (128 random
 * bits). INTERNAL. Shared kernel type, so {@code tools} can name a conversation without depending on
 * {@code conversation} (which depends on {@code agent} and therefore on {@code tools}).
 */
public record ConversationId(String value) {

    static final Pattern FORMAT = Pattern.compile("^cv_[A-Za-z0-9_-]{22}$");

    public ConversationId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid conversation id format");
        }
    }
}
