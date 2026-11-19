package com.secureerp.agent.conversation.internal;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chat limits (EP-04, DESIGN PROPOSAL; D-10 covers tool calls, these cover HTTP turns so that model calls
 * cannot be driven without bound): new conversations per principal per hour (same value as the tool
 * quota), customer messages per conversation and per principal per hour, request body size, and how long
 * an unanswered turn counts as running (after that it is completed as handed off on replay).
 */
@ConfigurationProperties("apas.conversation")
record ConversationProperties(int newConversationsPerPrincipalPerHour, int messagesPerConversationPerHour,
        int messagesPerPrincipalPerHour, int maxRequestBytes, Duration turnTimeout) {

    ConversationProperties {
        turnTimeout = Objects.requireNonNullElse(turnTimeout, Duration.ofMinutes(2));
        if (newConversationsPerPrincipalPerHour < 1 || messagesPerConversationPerHour < 1
                || messagesPerPrincipalPerHour < 1 || maxRequestBytes < 256 || turnTimeout.isNegative()
                || turnTimeout.isZero()) {
            throw new IllegalArgumentException("invalid conversation limits");
        }
    }
}
