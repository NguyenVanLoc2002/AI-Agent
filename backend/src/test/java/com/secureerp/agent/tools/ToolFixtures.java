package com.secureerp.agent.tools;

import com.secureerp.agent.identity.Channel;
import com.secureerp.agent.identity.IdentityFixtures;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.platform.ConversationId;
import java.util.UUID;

/** Synthetic tool-call contexts (no real users, conversations or sessions). */
public final class ToolFixtures {

    private ToolFixtures() {
    }

    public static ConversationId conversation(int seed) {
        return new ConversationId("cv_" + ("Conv" + seed + "ZZZZZZZZZZZZZZZZZZZZZZ").substring(0, 22));
    }

    public static String turn(long seed) {
        return new UUID(0x7001_0000_0000_0000L | seed, 0x8000_0000_0000_0000L | seed).toString();
    }

    public static ToolCallContext customer(String principalSeed, int conversationSeed, long turnSeed) {
        return new ToolCallContext(IdentityFixtures.principal(principalSeed), Role.CUST, Channel.WEB_CHAT,
                conversation(conversationSeed), turn(turnSeed));
    }

    public static ToolCallContext withRole(ToolCallContext context, Role role, Channel channel) {
        return new ToolCallContext(context.principal(), role, channel, context.conversation(), context.turnId());
    }
}
