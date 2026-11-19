package com.secureerp.agent.tools;

import com.secureerp.agent.identity.AuthenticatedCustomerRef;
import com.secureerp.agent.identity.Channel;
import com.secureerp.agent.identity.IdentityFixtures;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.identity.PrincipalId;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.tools.spi.ConversationAccessPort;
import com.secureerp.agent.tools.spi.ConversationAccessPort.Access;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.UUID;

/** Synthetic tool-call contexts (no real users, conversations or sessions). */
public final class ToolFixtures {

    /** Synthetic Mock ERP customers (MockErpBackend): A is in good standing, B is blocked for credit. */
    public static final String CUSTOMER_A = "SynthCustomerA";
    public static final String CUSTOMER_B = "SynthCustomerB";

    private ToolFixtures() {
    }

    /**
     * A conversation access port for tests: {@code check} decides ownership, {@code customers} the
     * customer each conversation is bound to (A-35; {@code null} = no customer).
     */
    public static ConversationAccessPort accessPort(BiFunction<PrincipalId, ConversationId, Access> check,
            Function<ConversationId, AuthenticatedCustomerRef> customers) {
        return new ConversationAccessPort() {
            @Override
            public Access check(PrincipalId principal, ConversationId conversation) {
                return check.apply(principal, conversation);
            }

            @Override
            public Optional<AuthenticatedCustomerRef> boundCustomer(PrincipalId principal, ConversationId conversation) {
                return Optional.ofNullable(customers.apply(conversation));
            }
        };
    }

    /** Every conversation is owned and bound to customer A. */
    public static ConversationAccessPort ownedByCustomerA() {
        return accessPort((principal, conversation) -> Access.ACTIVE,
                conversation -> IdentityFixtures.customer(CUSTOMER_A));
    }

    public static ConversationId conversation(int seed) {
        return new ConversationId("cv_" + ("Conv" + seed + "ZZZZZZZZZZZZZZZZZZZZZZ").substring(0, 22));
    }

    public static String turn(long seed) {
        return new UUID(0x7001_0000_0000_0000L | seed, 0x8000_0000_0000_0000L | seed).toString();
    }

    public static ToolCallContext customer(String principalSeed, int conversationSeed, long turnSeed) {
        return new ToolCallContext(IdentityFixtures.principal(principalSeed), Role.CUST, Channel.WEB_CHAT,
                conversation(conversationSeed), turn(turnSeed), IdentityFixtures.customer(CUSTOMER_A));
    }

    public static ToolCallContext withRole(ToolCallContext context, Role role, Channel channel) {
        return new ToolCallContext(context.principal(), role, channel, context.conversation(), context.turnId(),
                context.customer());
    }

    /** The same call for another session customer ({@code null}: no linked customer). */
    public static ToolCallContext withCustomer(ToolCallContext context, AuthenticatedCustomerRef customer) {
        return new ToolCallContext(context.principal(), context.role(), context.channel(), context.conversation(),
                context.turnId(), customer);
    }
}
