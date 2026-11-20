package com.secureerp.agent.order.internal;

import com.secureerp.agent.order.DraftOrderState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code draft_order_request} and {@code draft_order_event} (Flyway V10). Every state change is a
 * conditional update on the expected current state, so two writers can never both win; the caller
 * owns the transaction. No customer key and no price is stored here.
 */
@Repository
class DraftOrderStore {

    record Request(long id, String quoteRef, int quoteVersion, String idempotencyKey, DraftOrderState state,
            String erpOrderRef, String lateErpOrderRef, String payloadDigest, Instant createdAt, Instant submittedAt,
            Instant completedAt) {

        @Override
        public String toString() {
            return "Request[" + state + ", redacted]";
        }
    }

    private static final String COLUMNS = "id, quote_ref, quote_version, idempotency_key, status, erp_order_ref, "
            + "late_erp_order_ref, payload_digest, created_at, submitted_at, completed_at";

    private final JdbcTemplate jdbc;

    DraftOrderStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return the new request's id; empty when the quote version already has a request */
    Optional<Long> insertPending(String quoteRef, int quoteVersion, String idempotencyKey, String payloadDigest,
            String team, Instant now) {
        return jdbc.queryForList("""
                insert into draft_order_request (quote_ref, quote_version, idempotency_key, payload_digest, team,
                                                 status, created_at)
                values (?, ?, ?, ?, ?, 'PENDING', ?)
                on conflict (quote_ref, quote_version) do nothing
                returning id
                """, Long.class, quoteRef, quoteVersion, idempotencyKey, payloadDigest, team, Timestamp.from(now))
                .stream().findFirst();
    }

    /** A request of this team only (rule 20: ownership on every row access). */
    Optional<Request> findForTeam(long id, String team) {
        return jdbc.query("select " + COLUMNS + " from draft_order_request where id = ? and team = ?",
                (rs, row) -> toRequest(rs), id, team).stream().findFirst();
    }

    Optional<Request> find(String quoteRef, int quoteVersion) {
        return jdbc.query("select " + COLUMNS + " from draft_order_request where quote_ref = ? and quote_version = ?",
                (rs, row) -> toRequest(rs), quoteRef, quoteVersion).stream().findFirst();
    }

    Optional<Request> findById(long id) {
        return jdbc.query("select " + COLUMNS + " from draft_order_request where id = ?",
                (rs, row) -> toRequest(rs), id).stream().findFirst();
    }

    /** PENDING → SUBMITTED for a request registered after {@code registeredAfter}; at most one caller wins. */
    Optional<Long> claim(String quoteRef, int quoteVersion, boolean keyForwarded, Instant registeredAfter, Instant now) {
        return jdbc.queryForList("""
                update draft_order_request
                set status = 'SUBMITTED', submitted_at = ?, key_forwarded = ?, row_version = row_version + 1
                where quote_ref = ? and quote_version = ? and status = 'PENDING' and created_at > ?
                returning id
                """, Long.class, Timestamp.from(now), keyForwarded, quoteRef, quoteVersion,
                Timestamp.from(registeredAfter)).stream().findFirst();
    }

    /** Guarded transition; false when the request is no longer in {@code from}. */
    boolean transition(long id, DraftOrderState from, DraftOrderState to, String erpOrderRef, String detailCode,
            Instant now) {
        if (!from.canBecome(to)) {
            throw new IllegalStateException("draft order transition not allowed: " + from + " -> " + to);
        }
        return jdbc.update("""
                update draft_order_request
                set status = ?, erp_order_ref = coalesce(?, erp_order_ref), detail_code = coalesce(?, detail_code),
                    completed_at = ?, row_version = row_version + 1
                where id = ? and status = ?
                """, to.name(), erpOrderRef, detailCode, Timestamp.from(now), id, from.name()) == 1;
    }

    private static final String UNFINISHED =
            "((status = 'SUBMITTED' and submitted_at <= ?) or (status = 'PENDING' and created_at <= ?))";

    /** Ids of requests left unfinished since before {@code cutoff}, oldest first. */
    List<Long> unfinishedBefore(Instant cutoff, int limit) {
        return jdbc.queryForList("select id from draft_order_request where " + UNFINISHED + " order by id limit ?",
                Long.class, Timestamp.from(cutoff), Timestamp.from(cutoff), limit);
    }

    /**
     * One request left unfinished since before {@code cutoff} becomes OUTCOME_UNKNOWN: a SUBMITTED one
     * may have reached the ERP, and a PENDING one is no longer sent (ADR-009: no late or repeated write).
     * Empty when the request finished in the meantime.
     */
    Optional<Request> expireUnfinished(long id, Instant cutoff, Instant now) {
        return jdbc.query("""
                update draft_order_request
                set status = 'OUTCOME_UNKNOWN', detail_code = 'NO_RECORDED_ANSWER', completed_at = ?,
                    row_version = row_version + 1
                where id = ? and %s
                returning %s
                """.formatted(UNFINISHED, COLUMNS), (rs, row) -> toRequest(rs), Timestamp.from(now), id,
                Timestamp.from(cutoff), Timestamp.from(cutoff)).stream().findFirst();
    }

    /**
     * Keeps what the ERP answered after the request was already counted as unknown, whatever state it
     * is in by now: evidence for the staff member who reconciles it. The state does not change.
     */
    void recordLateAnswer(long id, String erpOrderRef, String detailCode) {
        jdbc.update("""
                update draft_order_request
                set late_erp_order_ref = coalesce(?, late_erp_order_ref), detail_code = coalesce(?, detail_code),
                    row_version = row_version + 1
                where id = ?
                """, erpOrderRef, detailCode, id);
    }

    List<Request> unknown(String team, int limit) {
        return jdbc.query("select " + COLUMNS + " from draft_order_request where status = 'OUTCOME_UNKNOWN' "
                + "and team = ? order by created_at, id limit ?", (rs, row) -> toRequest(rs), team, limit);
    }

    /** EP-08b: the team's created drafts without a staff decision, oldest first. */
    List<Request> awaitingDecision(String team, int limit) {
        return jdbc.query("select " + COLUMNS + " from draft_order_request where status in ('CREATED', "
                + "'RECONCILED_CREATED') and team = ? order by completed_at, id limit ?", (rs, row) -> toRequest(rs),
                team, limit);
    }

    /**
     * EP-08b: guarded staff decision. Keeps the ERP draft number and the completion time of the created
     * draft (the decision's time and actor are a {@code draft_order_event} row). False when the request is
     * no longer in {@code from}.
     */
    boolean decide(long id, DraftOrderState from, DraftOrderState to) {
        if (!from.canBecome(to)) {
            throw new IllegalStateException("draft order transition not allowed: " + from + " -> " + to);
        }
        return jdbc.update("update draft_order_request set status = ?, row_version = row_version + 1 "
                + "where id = ? and status = ?", to.name(), id, from.name()) == 1;
    }

    void event(long requestId, String event, String actor, Instant now) {
        jdbc.update("insert into draft_order_event (request_id, event, actor, occurred_at) values (?, ?, ?, ?)",
                requestId, event, actor, Timestamp.from(now));
    }

    private static Request toRequest(ResultSet rs) throws SQLException {
        return new Request(rs.getLong("id"), rs.getString("quote_ref"), rs.getInt("quote_version"),
                rs.getString("idempotency_key"), DraftOrderState.valueOf(rs.getString("status")),
                rs.getString("erp_order_ref"), rs.getString("late_erp_order_ref"), rs.getString("payload_digest"),
                instant(rs, "created_at"),
                instant(rs, "submitted_at"),
                instant(rs, "completed_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
