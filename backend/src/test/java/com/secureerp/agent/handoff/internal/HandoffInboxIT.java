package com.secureerp.agent.handoff.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.TestcontainersConfiguration;
import com.secureerp.agent.handoff.HandoffInbox;
import com.secureerp.agent.handoff.HandoffInbox.Item;
import com.secureerp.agent.handoff.HandoffInbox.Status;
import com.secureerp.agent.handoff.HandoffReason;
import com.secureerp.agent.handoff.Handoffs;
import com.secureerp.agent.identity.IdentityFixtures;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.tools.ToolFixtures;
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
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * EP-08a handoff states against PostgreSQL (Flyway V11): what the HTTP test cannot show with one
 * member per demo team. Two members of one team, claim races, and the guards of each transition.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class HandoffInboxIT {

    private static final ConversationId CONVERSATION = ToolFixtures.conversation(801);

    @Autowired
    Handoffs handoffs;

    @Autowired
    HandoffInbox inbox;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transactions;

    private final SessionIdentity anna = IdentityFixtures.salesIdentity("annaT1", IdentityFixtures.TEAM_1);
    private final SessionIdentity binh = IdentityFixtures.salesIdentity("binhT1", IdentityFixtures.TEAM_1);
    private final SessionIdentity otherTeam = IdentityFixtures.salesIdentity("chiT2", IdentityFixtures.TEAM_2);

    @BeforeEach
    void clean() {
        jdbc.update("delete from handoff");
    }

    private long open() {
        handoffs.open(CONVERSATION, HandoffReason.QUOTE_NOT_ISSUED, IdentityFixtures.TEAM_1);
        return inbox.list(anna).getFirst().handoffId();
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApplicationException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void oneActiveHandoffPerConversationKeepsItsFirstReasonAndTeam() {
        long id = open();
        handoffs.open(CONVERSATION, HandoffReason.QUOTE_NOT_ISSUED, IdentityFixtures.TEAM_2); // the same reason again
        inbox.claim(anna, id);

        assertThat(jdbc.queryForList("select reason_code || ':' || team || ':' || status || ':' || "
                + "coalesce(followup_reason, '-') from handoff", String.class))
                .containsExactly("QUOTE_NOT_ISSUED:team-1:CLAIMED:-");
        assertThat(inbox.list(otherTeam)).isEmpty();
        // After it is resolved, a new handoff may open.
        inbox.resolve(anna, id);
        assertThat(inbox.list(anna)).isEmpty();
        handoffs.open(CONVERSATION, HandoffReason.ERP_UNKNOWN, IdentityFixtures.TEAM_1);
        assertThat(inbox.list(anna)).extracting(Item::reasonCode).containsExactly("ERP_UNKNOWN");
    }

    /**
     * Reviews 2026-11-19 (security M1, backend L1; A-41(p)): a different reason raised while a handoff is
     * active is not dropped. The active handoff shows it, and resolving opens a new handoff with it, so
     * the conversation is not handed back to the bot with an unanswered reason.
     */
    @Test
    void aReasonRaisedWhileAHandoffIsActiveBecomesAFollowUpHandoff() {
        long id = open();
        inbox.claim(anna, id);
        handoffs.open(CONVERSATION, HandoffReason.ERP_UNKNOWN, IdentityFixtures.TEAM_1);
        handoffs.open(CONVERSATION, HandoffReason.PROVIDER_ERROR, IdentityFixtures.TEAM_1); // only the first is kept

        assertThat(inbox.list(anna)).extracting(Item::followUpReasonCode).containsExactly("ERP_UNKNOWN");

        assertThat(inbox.resolve(anna, id).status()).isEqualTo(Status.RESOLVED);

        List<Item> reopened = inbox.list(anna);
        assertThat(reopened).extracting(Item::reasonCode).containsExactly("ERP_UNKNOWN");
        assertThat(reopened.getFirst().status()).isEqualTo(Status.OPEN);
        assertThat(reopened.getFirst().handoffId()).isNotEqualTo(id);
        assertThat(handoffs.heldBy(CONVERSATION, anna.principal())).isFalse();
    }

    /** Review round 2: a reason raised again after staff claimed (a second blocked quote) is a follow-up too. */
    @Test
    void theSameReasonRaisedAfterTheClaimIsAFollowUpButNotBeforeIt() {
        long id = open();
        handoffs.open(CONVERSATION, HandoffReason.QUOTE_NOT_ISSUED, IdentityFixtures.TEAM_1); // staff have not looked yet
        assertThat(inbox.list(anna)).extracting(Item::followUpReasonCode).containsOnlyNulls();
        inbox.claim(anna, id);

        handoffs.open(CONVERSATION, HandoffReason.QUOTE_NOT_ISSUED, IdentityFixtures.TEAM_1);

        assertThat(inbox.list(anna)).extracting(Item::followUpReasonCode).containsExactly("QUOTE_NOT_ISSUED");
        inbox.resolve(anna, id);
        assertThat(inbox.list(anna)).extracting(Item::status).containsExactly(Status.OPEN);
    }

    /**
     * Review round 2 (A-42(n)): the handoff lock itself. While one transaction holds a conversation's lock
     * (here: a staff write being checked), a claim of that conversation waits for it; a claim on another
     * conversation does not.
     */
    @Test
    void changesOfOneConversationWaitForEachOther() throws Exception {
        long id = open();
        ConversationId other = ToolFixtures.conversation(802);
        handoffs.open(other, HandoffReason.UNSURE, IdentityFixtures.TEAM_1);
        long otherId = inbox.list(anna).stream().filter(item -> item.conversation().equals(other)).findFirst()
                .orElseThrow().handoffId();
        CountDownLatch holding = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            Future<?> holder = pool.submit(() -> transactions.executeWithoutResult(tx -> {
                handoffs.holdFor(CONVERSATION, anna.principal());
                holding.countDown();
                pause(900);
            }));
            await(holding);
            long start = System.nanoTime();
            inbox.claim(binh, otherId);
            long unrelated = (System.nanoTime() - start) / 1_000_000;
            inbox.claim(anna, id);
            long blocked = (System.nanoTime() - start) / 1_000_000;
            holder.get();

            assertThat(unrelated).as("another conversation is not blocked").isLessThan(500);
            assertThat(blocked).as("the same conversation waits for the lock holder").isGreaterThanOrEqualTo(600);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Reviews 2026-11-19 (backend M1, architecture H1): a handoff opened while another is being resolved
     * waits for the resolve and is then recorded; it is never skipped against the row being resolved.
     */
    @Test
    void aHandoffOpenedDuringAResolveIsNotLost() throws Exception {
        long id = open();
        inbox.claim(anna, id);
        CountDownLatch resolving = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> resolve = pool.submit(() -> transactions.executeWithoutResult(tx -> {
                inbox.resolve(anna, id);
                resolving.countDown();
                pause(700); // the resolve is not committed yet
            }));
            Future<?> open = pool.submit(() -> {
                await(resolving);
                transactions.executeWithoutResult(tx ->
                        handoffs.open(CONVERSATION, HandoffReason.ERP_UNKNOWN, IdentityFixtures.TEAM_1));
            });
            resolve.get();
            open.get();
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForList("select reason_code || ':' || status from handoff order by handoff_id",
                String.class)).containsExactly("QUOTE_NOT_ISSUED:RESOLVED", "ERP_UNKNOWN:OPEN");
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void onlyOneMemberHoldsAHandoffAndOnlyTheyResolveIt() {
        long id = open();
        assertThat(handoffs.heldBy(CONVERSATION, anna.principal())).isFalse();
        assertCode(() -> inbox.resolve(anna, id), ErrorCode.CONFLICT); // not claimed yet

        assertThat(inbox.claim(anna, id).status()).isEqualTo(Status.CLAIMED);
        assertThat(inbox.claim(anna, id).mine()).isTrue(); // the same claim again
        assertCode(() -> inbox.claim(binh, id), ErrorCode.CONFLICT);
        assertCode(() -> inbox.resolve(binh, id), ErrorCode.CONFLICT);
        assertThat(handoffs.heldBy(CONVERSATION, anna.principal())).isTrue();
        assertThat(handoffs.heldBy(CONVERSATION, binh.principal())).isFalse();
        assertThat(inbox.list(binh)).extracting(Item::mine).containsExactly(false);

        assertThat(inbox.resolve(anna, id).status()).isEqualTo(Status.RESOLVED);
        assertThat(inbox.resolve(anna, id).status()).isEqualTo(Status.RESOLVED); // the same resolve again
        assertThat(handoffs.heldBy(CONVERSATION, anna.principal())).as("A-15: access ends at resolve").isFalse();
        assertCode(() -> inbox.claim(binh, id), ErrorCode.CONFLICT);
        assertCode(() -> inbox.resolve(binh, id), ErrorCode.CONFLICT);
    }

    @Test
    void tenParallelClaimsHaveOneWinner() throws Exception {
        long id = open();
        List<SessionIdentity> members = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            members.add(IdentityFixtures.salesIdentity("racer" + i, IdentityFixtures.TEAM_1));
        }
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (SessionIdentity member : members) {
                Callable<Boolean> call = () -> {
                    start.await();
                    try {
                        return inbox.claim(member, id).mine();
                    } catch (ApplicationException refused) {
                        assertThat(refused.code()).isEqualTo(ErrorCode.CONFLICT);
                        return false;
                    }
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            int winners = 0;
            for (Future<Boolean> result : results) {
                winners += result.get() ? 1 : 0;
            }
            assertThat(winners).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(members.stream().filter(member -> handoffs.heldBy(CONVERSATION, member.principal())).count())
                .isEqualTo(1);
    }

    /** T-UC06-XTEAM / T-UC06-OPS at the service: another team is 404, another role 403. */
    @Test
    void otherTeamsGetNotFoundAndOtherRolesForbidden() {
        long id = open();
        assertThat(inbox.list(otherTeam)).isEmpty();
        assertCode(() -> inbox.claim(otherTeam, id), ErrorCode.NOT_FOUND);
        assertCode(() -> inbox.resolve(otherTeam, id), ErrorCode.NOT_FOUND);
        assertCode(() -> inbox.claim(anna, id + 1000), ErrorCode.NOT_FOUND);
        for (SessionIdentity other : List.of(IdentityFixtures.staffIdentity("ops1", Role.OPS),
                IdentityFixtures.staffIdentity("secrev1", Role.SECREV),
                IdentityFixtures.sessionIdentity("custA", "SynthCustomerA"))) {
            assertCode(() -> inbox.list(other), ErrorCode.FORBIDDEN);
            assertCode(() -> inbox.claim(other, id), ErrorCode.FORBIDDEN);
            assertCode(() -> inbox.resolve(other, id), ErrorCode.FORBIDDEN);
        }
        assertThat(inbox.list(anna)).extracting(Item::status).containsExactly(Status.OPEN);
    }

    @Test
    void databaseConstraintsBackTheStates() {
        long id = open();
        assertThatThrownBy(() -> jdbc.update("update handoff set status = 'CLAIMED' where handoff_id = ?", id))
                .as("claimed needs a holder").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update handoff set status = 'CANCELLED' where handoff_id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into handoff (conversation_id, reason_code, status, opened_at) "
                + "values (?, 'UNSURE', 'OPEN', now())", CONVERSATION.value()))
                .as("one active handoff per conversation").isInstanceOf(DataIntegrityViolationException.class);
    }
}
