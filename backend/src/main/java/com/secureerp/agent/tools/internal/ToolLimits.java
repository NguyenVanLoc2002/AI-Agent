package com.secureerp.agent.tools.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tool limits (TOOL_CONTRACTS §1; D-10 restrictive defaults APPROVED 2026-11-18). Configurable, but
 * the defaults are the approved values; every limit must be positive.
 *
 * @param callsPerTurn                     tool calls per model turn
 * @param callsPerConversationPerHour      tool calls per conversation per rolling hour
 * @param callsPerPrincipalPerHour         tool calls per principal per rolling hour, all conversations
 * @param conversationsPerPrincipalPerHour distinct conversations with tool calls per principal per hour
 * @param maxDepth                         JSON nesting depth of the arguments (root object = 1)
 * @param maxArrayItems                    items per JSON array in the arguments
 * @param maxStringChars                   characters per JSON string in the arguments
 * @param rejectionsBeforeHandoff          rejected calls per conversation per hour before handoff
 * @param productRefTtl                    lifetime of an issued productRef
 */
@ConfigurationProperties("apas.tools.limits")
public record ToolLimits(
        @DefaultValue("3") int callsPerTurn,
        @DefaultValue("20") int callsPerConversationPerHour,
        @DefaultValue("40") int callsPerPrincipalPerHour,
        @DefaultValue("5") int conversationsPerPrincipalPerHour,
        @DefaultValue("3") int maxDepth,
        @DefaultValue("20") int maxArrayItems,
        @DefaultValue("256") int maxStringChars,
        @DefaultValue("3") int rejectionsBeforeHandoff,
        @DefaultValue("PT2H") Duration productRefTtl) {

    /** The approved D-10 values. */
    public static final ToolLimits DEFAULTS = new ToolLimits(3, 20, 40, 5, 3, 20, 256, 3, Duration.ofHours(2));

    /** Rolling window of the hourly limits. */
    public static final Duration WINDOW = Duration.ofHours(1);

    public ToolLimits {
        if (callsPerTurn < 1 || callsPerConversationPerHour < 1 || callsPerPrincipalPerHour < 1
                || conversationsPerPrincipalPerHour < 1 || maxDepth < 1 || maxArrayItems < 1 || maxStringChars < 1
                || rejectionsBeforeHandoff < 1 || productRefTtl == null || productRefTtl.isNegative()
                || productRefTtl.isZero()) {
            throw new IllegalArgumentException("apas.tools.limits values must be positive");
        }
    }
}
