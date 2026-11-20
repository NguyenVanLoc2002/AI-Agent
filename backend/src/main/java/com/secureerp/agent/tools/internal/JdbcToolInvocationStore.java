package com.secureerp.agent.tools.internal;

import com.secureerp.agent.platform.TraceIds;
import com.secureerp.agent.tools.ToolCallContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.OptionalLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL implementation. {@link #admit} serialises the calls of one principal with a transaction
 * advisory lock in a namespace of its own, so concurrent calls cannot both take the last free quota
 * slot. Writes run in their own transaction: a caller rollback must not erase attempts that counted.
 * Every counter is scoped to the calling principal.
 */
@Repository
class JdbcToolInvocationStore implements ToolInvocationStore {

    /** Advisory-lock namespace of the tool quota (first key of the two-key form). */
    static final int LOCK_NAMESPACE = 30_003;

    /** Rows that consumed quota: everything except catalog-filter audit rows. */
    private static final String COUNTED = " and decision <> 'FILTERED'";

    /**
     * Rows of a call that got past the quota check. A conversation counts as "used" only through such
     * rows, so a quota rejection in a new conversation does not make it look used (security review M-1).
     */
    private static final String ADMITTED = " and decision <> 'FILTERED'"
            + " and not (decision = 'REJECTED' and reason_code = 'QUOTA_EXCEEDED')";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate ownTransaction;

    JdbcToolInvocationStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public OptionalLong admit(ToolCallContext context, String toolName, boolean ownsConversation, Instant now,
            ToolLimits limits) {
        String principal = context.principal().pseudonym();
        String conversation = ownsConversation ? context.conversation().value() : null;
        Timestamp since = Timestamp.from(now.minus(ToolLimits.WINDOW));
        Long id = ownTransaction.execute(status -> {
            jdbc.queryForList("select pg_advisory_xact_lock(?, hashtext(?))", LOCK_NAMESPACE, principal);
            int forPrincipal = count("select count(*) from tool_invocation where principal_key = ? and occurred_at >= ?"
                    + COUNTED, principal, since);
            boolean exhausted = forPrincipal >= limits.callsPerPrincipalPerHour();
            if (conversation != null && !exhausted) {
                int inTurn = count("select count(*) from tool_invocation where principal_key = ? and turn_id = ?"
                        + COUNTED, principal, context.turnId());
                int inConversation = count("select count(*) from tool_invocation where principal_key = ?"
                        + " and conversation_id = ? and occurred_at >= ?" + COUNTED, principal, conversation, since);
                int usedHere = count("select count(*) from tool_invocation where principal_key = ?"
                        + " and conversation_id = ? and occurred_at >= ?" + ADMITTED, principal, conversation, since);
                int otherConversations = count("select count(distinct conversation_id) from tool_invocation"
                        + " where principal_key = ? and occurred_at >= ? and conversation_id <> ?" + ADMITTED,
                        principal, since, conversation);
                exhausted = inTurn >= limits.callsPerTurn()
                        || inConversation >= limits.callsPerConversationPerHour()
                        || (usedHere == 0 && otherConversations >= limits.conversationsPerPrincipalPerHour());
            }
            if (exhausted) {
                insert(context, conversation, toolName, Decision.REJECTED, "QUOTA_EXCEEDED", now);
                return null;
            }
            return insert(context, conversation, toolName, Decision.ADMITTED, null, now);
        });
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    @Override
    public void complete(long invocationId, Decision decision, String reasonCode) {
        ownTransaction.executeWithoutResult(status -> jdbc.update(
                "update tool_invocation set decision = ?, reason_code = ? where id = ? and decision = 'ADMITTED'",
                decision.name(), reasonCode, invocationId));
    }

    @Override
    public void recordFiltered(ToolCallContext context, String toolName, Instant now) {
        ownTransaction.executeWithoutResult(status -> insert(context, context.conversation().value(), toolName,
                Decision.FILTERED, "CATALOG_FIELD_REJECTED", now));
    }

    @Override
    public int rejectionsSince(ToolCallContext context, Instant since) {
        return count("select count(*) from tool_invocation where principal_key = ? and conversation_id = ?"
                + " and occurred_at >= ? and decision = 'REJECTED'", context.principal().pseudonym(),
                context.conversation().value(), Timestamp.from(since));
    }

    private Long insert(ToolCallContext context, String conversation, String toolName, Decision decision,
            String reasonCode, Instant now) {
        return jdbc.queryForObject("""
                insert into tool_invocation
                    (principal_key, conversation_id, turn_id, tool_name, decision, reason_code, occurred_at, request_id)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """, Long.class, context.principal().pseudonym(), conversation, context.turnId(), toolName,
                decision.name(), reasonCode, Timestamp.from(now), TraceIds.current().orElse(null));
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
