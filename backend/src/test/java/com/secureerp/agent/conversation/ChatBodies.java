package com.secureerp.agent.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat answer bodies for sentinel checks (IMPLEMENTATION_PLAN §2a SEC01-FLAKE-UUID, owner 2026-11-23): the
 * {@code turnId} is a random UUID the server draws, so a short sentinel such as "555" or "401" can occur in
 * it by chance. Only that one value is removed, and only in UUID form; every other part of the body,
 * the reply included, is still checked.
 */
public final class ChatBodies {

    private static final Pattern TURN_ID =
            Pattern.compile("\"turnId\":\"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\"");

    private ChatBodies() {
    }

    /** The body with its single {@code turnId} replaced by a fixed value; fails when there is none or several. */
    public static String withoutTurnId(String body) {
        Matcher matcher = TURN_ID.matcher(body);
        assertThat(matcher.find()).as("one turnId in %s", body).isTrue();
        String stripped = matcher.replaceFirst("\"turnId\":\"-\"");
        assertThat(TURN_ID.matcher(stripped).find()).as("one turnId only").isFalse();
        return stripped;
    }
}
