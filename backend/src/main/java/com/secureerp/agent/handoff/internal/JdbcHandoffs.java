package com.secureerp.agent.handoff.internal;

import com.secureerp.agent.handoff.ConversationNotFollowing;
import com.secureerp.agent.handoff.HandoffClaimed;
import com.secureerp.agent.handoff.HandoffInbox;
import com.secureerp.agent.handoff.HandoffReason;
import com.secureerp.agent.handoff.HandoffResolved;
import com.secureerp.agent.handoff.Handoffs;
import com.secureerp.agent.identity.PrincipalId;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.identity.Team;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.platform.ErrorCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code handoff} table (Flyway V9, V11). At most one handoff that is not resolved per conversation
 * (partial unique index). Every staff read and write filters by the staff member's team (rule 20:
 * ownership on every row access); claim and resolve are conditional updates on the expected status, and
 * publish their event inside the same transaction so the conversation follows atomically. Logs carry
 * codes only.
 */
@Component
class JdbcHandoffs implements Handoffs, HandoffInbox {

    private static final Logger log = LoggerFactory.getLogger(JdbcHandoffs.class);
    static final int MAX_LISTED = 50;

    /** Advisory lock namespace of handoff changes per conversation (conversation 30_010/30_011, quote 30_006). */
    static final int LOCK_NAMESPACE = 30_012;

    private record Row(long id, String conversation, String reason, Status status, Timestamp openedAt,
            String claimedBy, String followUp) {
    }

    private static final String COLUMNS =
            "handoff_id, conversation_id, reason_code, status, opened_at, claimed_by, followup_reason";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    JdbcHandoffs(JdbcTemplate jdbc, TransactionTemplate transactions, ApplicationEventPublisher events, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public void open(ConversationId conversation, HandoffReason reason, Team team) {
        Objects.requireNonNull(conversation, "conversation");
        Objects.requireNonNull(reason, "reason");
        // Joins the caller's transaction, or runs in its own, so the lock always lasts until the write commits.
        int inserted = Objects.requireNonNull(transactions.execute(tx -> {
            lock(conversation.value());
            int created = insertOpen(conversation.value(), reason.name(), team == null ? null : team.value());
            if (created == 0) {
                // Not lost: the active handoff remembers the first reason that is new to the staff member (a
                // different one, or any one raised after they claimed) and resolving reopens with it.
                jdbc.update("update handoff set followup_reason = ?, row_version = row_version + 1 "
                        + "where conversation_id = ? and status in ('OPEN', 'CLAIMED') and followup_reason is null "
                        + "and (reason_code <> ? or status = 'CLAIMED')", reason.name(), conversation.value(),
                        reason.name());
            }
            return created;
        }));
        if (team == null) {
            // Visible to no staff member until a team is configured (D-08, A-42): make it loud.
            log.warn("handoff.open reason={} new={} assigned=false", reason, inserted == 1);
        } else {
            log.info("handoff.open reason={} new={} assigned=true", reason, inserted == 1);
        }
    }

    private int insertOpen(String conversation, String reason, String team) {
        return jdbc.update("insert into handoff (conversation_id, reason_code, status, team, opened_at) "
                        + "values (?, ?, 'OPEN', ?, ?) "
                        + "on conflict (conversation_id) where status in ('OPEN', 'CLAIMED') do nothing",
                conversation, reason, team, Timestamp.from(clock.instant()));
    }

    /**
     * Serialises open, claim, resolve and staff writes of one conversation for the current transaction,
     * so a handoff opened while another is being resolved is neither skipped nor left on an unlocked
     * conversation. Bounded wait; the waiter fails.
     */
    private void lock(String conversation) {
        jdbc.execute("set local lock_timeout = '5s'");
        jdbc.queryForList("select pg_advisory_xact_lock(?, hashtext(?))", LOCK_NAMESPACE, conversation);
    }

    @Override
    public boolean holdFor(ConversationId conversation, PrincipalId staff) {
        if (conversation == null || staff == null) {
            return false;
        }
        return Boolean.TRUE.equals(transactions.execute(tx -> {
            lock(conversation.value());
            return heldBy(conversation, staff);
        }));
    }

    @Override
    public boolean heldBy(ConversationId conversation, PrincipalId staff) {
        if (conversation == null || staff == null) {
            return false;
        }
        Integer held = jdbc.queryForObject("select count(*) from handoff where conversation_id = ? "
                + "and status = 'CLAIMED' and claimed_by = ?", Integer.class, conversation.value(), staff.pseudonym());
        return held != null && held == 1;
    }

    @Override
    public List<Item> list(SessionIdentity staff) {
        Team team = team(staff);
        String me = staff.principal().pseudonym();
        return jdbc.query("select " + COLUMNS + " from handoff where team = ? and status in ('OPEN', 'CLAIMED') "
                        + "order by opened_at, handoff_id limit ?", (rs, row) -> toRow(rs), team.value(), MAX_LISTED)
                .stream().map(row -> item(row, me)).toList();
    }

    @Override
    public Item claim(SessionIdentity staff, long handoffId) {
        Team team = team(staff);
        String me = staff.principal().pseudonym();
        return following(() -> transactions.execute(tx -> {
            lock(find(handoffId, team).conversation());
            Row row = find(handoffId, team);
            if (row.status() == Status.CLAIMED && me.equals(row.claimedBy())) {
                return item(row, me); // the same claim again
            }
            if (row.status() != Status.OPEN || jdbc.update("update handoff set status = 'CLAIMED', claimed_by = ?, "
                    + "claimed_at = ?, row_version = row_version + 1 where handoff_id = ? and team = ? "
                    + "and status = 'OPEN'", me, Timestamp.from(clock.instant()), handoffId, team.value()) != 1) {
                log.info("handoff.claim refused code=CONFLICT");
                throw new ApplicationException(ErrorCode.CONFLICT);
            }
            events.publishEvent(new HandoffClaimed(new ConversationId(row.conversation())));
            log.info("handoff.claim outcome=CLAIMED");
            return item(new Row(row.id(), row.conversation(), row.reason(), Status.CLAIMED, row.openedAt(), me,
                    row.followUp()), me);
        }));
    }

    @Override
    public Item resolve(SessionIdentity staff, long handoffId) {
        Team team = team(staff);
        String me = staff.principal().pseudonym();
        return following(() -> transactions.execute(tx -> {
            lock(find(handoffId, team).conversation());
            Row row = find(handoffId, team);
            if (row.status() == Status.RESOLVED && me.equals(row.claimedBy())) {
                return item(row, me); // the same resolve again
            }
            Timestamp now = Timestamp.from(clock.instant());
            if (row.status() != Status.CLAIMED || !me.equals(row.claimedBy())
                    || jdbc.update("update handoff set status = 'RESOLVED', resolved_by = ?, resolved_at = ?, "
                    + "row_version = row_version + 1 where handoff_id = ? and team = ? and status = 'CLAIMED' "
                    + "and claimed_by = ?", me, now, handoffId, team.value(), me) != 1) {
                log.info("handoff.resolve refused code=CONFLICT");
                throw new ApplicationException(ErrorCode.CONFLICT);
            }
            // A reason raised while this handoff was active gets its own handoff now; the bot stays locked.
            boolean followUp = row.followUp() != null && insertOpen(row.conversation(), row.followUp(), team.value()) == 1;
            events.publishEvent(new HandoffResolved(new ConversationId(row.conversation()), followUp));
            log.info("handoff.resolve outcome=RESOLVED followUp={}", followUp);
            return item(new Row(row.id(), row.conversation(), row.reason(), Status.RESOLVED, row.openedAt(), me,
                    row.followUp()), me);
        }));
    }

    /** A conversation that cannot follow rolls the change back; the caller gets a conflict, as for a lock timeout. */
    private static Item following(Supplier<Item> change) {
        try {
            return change.get();
        } catch (ConversationNotFollowing | PessimisticLockingFailureException refused) {
            log.error("handoff.change refused code=CONFLICT reason={}", refused.getClass().getSimpleName());
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }

    /** A handoff of another team and a missing one are the same 404 (AC-06.1). */
    private Row find(long handoffId, Team team) {
        Optional<Row> row = jdbc.query("select " + COLUMNS + " from handoff where handoff_id = ? and team = ?",
                (rs, n) -> toRow(rs), handoffId, team.value()).stream().findFirst();
        return row.orElseThrow(() -> {
            log.warn("handoff.access denied code=FORBIDDEN_RESOURCE");
            return new ApplicationException(ErrorCode.NOT_FOUND);
        });
    }

    /** RBAC_MATRIX: only SALES with a team; an administrator is not a business reader (BR-24). */
    private static Team team(SessionIdentity staff) {
        Objects.requireNonNull(staff, "staff");
        if (staff.role() != Role.SALES || staff.team().isEmpty()) {
            log.warn("handoff.access denied code=FORBIDDEN");
            throw new ApplicationException(ErrorCode.FORBIDDEN);
        }
        return staff.team().get();
    }

    private static Item item(Row row, String me) {
        return new Item(row.id(), new ConversationId(row.conversation()), row.reason(), row.status(),
                row.openedAt().toInstant(), me.equals(row.claimedBy()) && row.status() == Status.CLAIMED,
                row.followUp());
    }

    private static Row toRow(ResultSet rs) throws SQLException {
        return new Row(rs.getLong("handoff_id"), rs.getString("conversation_id"), rs.getString("reason_code"),
                Status.valueOf(rs.getString("status")), rs.getTimestamp("opened_at"), rs.getString("claimed_by"),
                rs.getString("followup_reason"));
    }
}
