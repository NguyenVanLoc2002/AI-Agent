package com.secureerp.agent.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.TestcontainersConfiguration;
import com.secureerp.agent.gateway.EgressScan;
import com.secureerp.agent.gateway.api.ProductSearchResult;
import com.secureerp.agent.gateway.api.ToolErrorResult;
import com.secureerp.agent.gateway.api.ToolProposal;
import com.secureerp.agent.identity.IdentityFixtures;
import com.secureerp.agent.policy.SentinelVariants;
import com.secureerp.agent.tools.spi.ConversationAccessPort;
import com.secureerp.agent.tools.spi.ConversationAccessPort.Access;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;

/**
 * EP-03 against PostgreSQL (rule 60: quota and ownership on a real database, with concurrency):
 * T-TOOL-04, T-TOOL-05, T-TOOL-12, audit metadata only, and the Mock ERP search end to end.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ToolRegistryIT {

    private static final String SEARCH = "searchVerifiedProduct";
    private static final ToolProposal SEARCH_OE = new ToolProposal(SEARCH, "{\"oe\":\"04465-0K290\"}");

    /** Every conversation is owned by its caller, except conversation 13 for principal "mallory". */
    @TestBean(methodName = "conversationAccess")
    ConversationAccessPort conversationAccess;

    /** EP-04: replaces the conversation module's port (exactly one port must exist, EP-03 LOW-2). */
    static ConversationAccessPort conversationAccess() {
        return ToolFixtures.accessPort((principal, conversation) -> principal.equals(IdentityFixtures.principal("mallory"))
                && conversation.equals(ToolFixtures.conversation(13)) ? Access.NOT_OWNED : Access.ACTIVE,
                conversation -> IdentityFixtures.customer(ToolFixtures.CUSTOMER_A));
    }

    @Autowired
    ToolManager tools;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() {
        jdbc.update("delete from tool_invocation");
        jdbc.update("delete from product_ref");
    }

    private static ToolErrorResult.Code code(ToolOutcome outcome) {
        return outcome.result() instanceof ToolErrorResult error ? error.code() : null;
    }

    @Test
    void searchThroughMockErpIssuesConversationBoundRefs() {
        ToolOutcome outcome = tools.execute(ToolFixtures.customer("alice", 1, 1), SEARCH_OE);

        ProductSearchResult result = (ProductSearchResult) outcome.result();
        assertThat(result.candidates()).isNotEmpty();
        assertThat(jdbc.queryForObject("select count(*) from product_ref where conversation_id = ?", Integer.class,
                ToolFixtures.conversation(1).value())).isEqualTo(result.candidates().size());
        assertThat(EgressScan.containsSentinel(result.toString())).isFalse();
    }

    /** T-TOOL-04: a productRef from another conversation (same or other principal) is forbidden. */
    @Test
    void productRefOfAnotherConversationIsForbidden() {
        ProductSearchResult result = (ProductSearchResult) tools.execute(ToolFixtures.customer("alice", 1, 1), SEARCH_OE)
                .result();
        String ref = result.candidates().getFirst().productRef();
        ToolProposal quote = new ToolProposal("requestQuote",
                "{\"lines\":[{\"productRef\":\"" + ref + "\",\"quantity\":2}]}");

        // Own conversation: ownership passes and the quote is issued (EP-06; no error code).
        assertThat(code(tools.execute(ToolFixtures.customer("alice", 1, 2), quote))).isNull();
        ToolOutcome otherConversation = tools.execute(ToolFixtures.customer("alice", 2, 3), quote);
        assertThat(code(otherConversation)).isEqualTo(ToolErrorResult.Code.FORBIDDEN_RESOURCE);
        assertThat(otherConversation.handoffRequired()).isTrue();
        assertThat(code(tools.execute(ToolFixtures.customer("bob", 3, 4), quote)))
                .isEqualTo(ToolErrorResult.Code.FORBIDDEN_RESOURCE);
        assertThat(code(tools.execute(ToolFixtures.customer("mallory", 13, 5), SEARCH_OE)))
                .isEqualTo(ToolErrorResult.Code.FORBIDDEN_RESOURCE);
    }

    /** T-TOOL-05: more than 3 calls in one turn, more than 20 in one conversation per hour. */
    @Test
    void perTurnAndPerConversationQuotas() {
        List<ToolErrorResult.Code> turn = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            turn.add(code(tools.execute(ToolFixtures.customer("carol", 4, 100), SEARCH_OE)));
        }
        assertThat(turn).containsExactly(null, null, null, ToolErrorResult.Code.QUOTA_EXCEEDED);

        List<ToolErrorResult.Code> conversation = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            conversation.add(code(tools.execute(ToolFixtures.customer("dave", 5, 200 + i), SEARCH_OE)));
        }
        assertThat(conversation.subList(0, 20)).containsOnlyNulls();
        assertThat(conversation.get(20)).isEqualTo(ToolErrorResult.Code.QUOTA_EXCEEDED);
    }

    /** T-TOOL-12: one principal opening many conversations to enumerate the catalog. */
    @Test
    void principalQuotaStopsEnumerationAcrossConversations() {
        int served = 0;
        for (int conversation = 100; conversation < 150; conversation++) {
            ToolOutcome outcome = tools.execute(ToolFixtures.customer("erin", conversation, conversation), SEARCH_OE);
            if (code(outcome) == null) {
                served++;
            } else {
                assertThat(code(outcome)).isEqualTo(ToolErrorResult.Code.QUOTA_EXCEEDED);
                assertThat(outcome.handoffRequired()).isTrue();
            }
        }
        assertThat(served).isEqualTo(5);

        int total = 0;
        for (int conversation = 200; conversation < 205; conversation++) {
            for (int call = 0; call < 9; call++) {
                if (code(tools.execute(ToolFixtures.customer("frank", conversation, conversation * 100L + call),
                        SEARCH_OE)) == null) {
                    total++;
                }
            }
        }
        assertThat(total).isEqualTo(40);
    }

    /** Concurrent calls in one turn cannot both take the last quota slot (advisory lock). */
    @Test
    void concurrentCallsRespectTheTurnQuota() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<ToolErrorResult.Code>> calls = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                calls.add(() -> {
                    start.await();
                    return code(tools.execute(ToolFixtures.customer("grace", 6, 300), SEARCH_OE));
                });
            }
            List<Future<ToolErrorResult.Code>> futures = new ArrayList<>();
            for (Callable<ToolErrorResult.Code> call : calls) {
                futures.add(pool.submit(call));
            }
            start.countDown();
            int served = 0;
            for (Future<ToolErrorResult.Code> future : futures) {
                if (future.get() == null) {
                    served++;
                }
            }
            assertThat(served).isEqualTo(3);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Audit rows hold metadata only: no argument values, never the raw unknown tool name. */
    @Test
    void auditRowsContainNoArgumentValues() {
        tools.execute(ToolFixtures.customer("heidi", 7, 400),
                new ToolProposal(SEARCH, "{\"oe\":\"04465-0K290\",\"customerId\":\"" + SentinelVariants.COST + "\"}"));
        tools.execute(ToolFixtures.customer("heidi", 7, 401), new ToolProposal("approveOrder", "{\"id\":\"QX-77\"}"));

        List<String> rows = jdbc.queryForList("select concat_ws('|', principal_key, conversation_id, turn_id, tool_name,"
                + " decision, reason_code) from tool_invocation order by id", String.class);
        assertThat(rows).hasSize(2).noneMatch(EgressScan::containsSentinel).noneMatch(r -> r.contains("approveOrder"))
                .noneMatch(r -> r.contains("QX-77"));
        assertThat(rows.get(0)).endsWith("|searchVerifiedProduct|REJECTED|TOOL_SCHEMA");
        assertThat(rows.get(1)).endsWith("|UNKNOWN|REJECTED|TOOL_UNKNOWN");
    }

    /** EP-03 security review M-1: a quota-rejected call must not make a new conversation look used. */
    @Test
    void secondCallInAnExtraConversationIsStillRefused() {
        for (int conversation = 500; conversation < 505; conversation++) {
            assertThat(code(tools.execute(ToolFixtures.customer("judy", conversation, conversation), SEARCH_OE)))
                    .isNull();
        }
        assertThat(code(tools.execute(ToolFixtures.customer("judy", 505, 600), SEARCH_OE)))
                .isEqualTo(ToolErrorResult.Code.QUOTA_EXCEEDED);
        assertThat(code(tools.execute(ToolFixtures.customer("judy", 505, 601), SEARCH_OE)))
                .isEqualTo(ToolErrorResult.Code.QUOTA_EXCEEDED);
    }

    /** EP-03 security review M-2: calls on someone else's conversation cannot use up or lock it. */
    @Test
    void foreignCallsDoNotConsumeTheOwnersQuota() {
        for (int call = 0; call < 25; call++) {
            tools.execute(ToolFixtures.customer("mallory", 13, 700 + call), SEARCH_OE);
        }
        ToolOutcome owner = tools.execute(ToolFixtures.customer("ivan", 13, 800), SEARCH_OE);
        assertThat(code(owner)).isNull();
        assertThat(owner.handoffRequired()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from tool_invocation where conversation_id is null",
                Integer.class)).isPositive();
    }

    /** EP-03 architecture review M3: tokens the model has seen survive a rollback of the caller. */
    @Test
    void issuedProductRefsSurviveCallerRollback() {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            tools.execute(ToolFixtures.customer("ken", 14, 900), SEARCH_OE);
            tx.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select count(*) from product_ref where conversation_id = ?", Integer.class,
                ToolFixtures.conversation(14).value())).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from tool_invocation where conversation_id = ?",
                Integer.class, ToolFixtures.conversation(14).value())).isEqualTo(1);
    }
}
