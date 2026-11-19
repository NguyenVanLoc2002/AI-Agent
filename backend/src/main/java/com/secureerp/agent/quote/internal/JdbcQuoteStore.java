package com.secureerp.agent.quote.internal;

import com.secureerp.agent.platform.ConversationId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL implementation (Flyway V6). Writes run in their own transaction under a per-conversation
 * advisory lock ({@link #LOCK_NAMESPACE}), so concurrent requests in one conversation are serialised:
 * at most one ISSUED quote (the partial unique index enforces the same rule), the newest snapshot
 * wins, and the A-34 limits cannot be raced past.
 */
@Repository
class JdbcQuoteStore implements QuoteStore {

    /** Advisory lock namespace for quote writes (tools uses 30_003). */
    static final int LOCK_NAMESPACE = 30_006;

    /** A lost race (STALE_REQUEST, e.g. a double submit) is not the customer's doing and does not lock. */
    private static final String NOT_QUOTED_SINCE = """
            select count(*) from quote_request where conversation_id = ? and outcome = 'NOT_QUOTED'
              and reason_code <> 'STALE_REQUEST' and occurred_at > ?
            """;
    private static final String ISSUED_SINCE = """
            select count(*) from quote_request where conversation_id = ? and outcome = 'ISSUED' and occurred_at > ?
            """;

    private final JdbcTemplate jdbc;
    /** REQUIRES_NEW, READ COMMITTED: the statements after the lock must see rows committed while waiting. */
    private final TransactionTemplate ownTransaction;

    JdbcQuoteStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.ownTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public Optional<NotQuotedReason> gate(ConversationId conversation, Limits limits) {
        return blocked(conversation.value(), limits);
    }

    private Optional<NotQuotedReason> blocked(String conversation, Limits limits) {
        if (count(NOT_QUOTED_SINCE, conversation, limits.lockSince()) > 0) {
            return Optional.of(NotQuotedReason.HANDED_OFF);
        }
        if (count(ISSUED_SINCE, conversation, limits.issuedSince()) >= limits.maxIssued()) {
            return Optional.of(NotQuotedReason.REISSUE_LIMIT);
        }
        return Optional.empty();
    }

    private int count(String sql, String conversation, Instant since) {
        Integer count = jdbc.queryForObject(sql, Integer.class, conversation, Timestamp.from(since));
        return count == null ? 0 : count;
    }

    private void lock(String conversation) {
        jdbc.queryForList("select pg_advisory_xact_lock(?, hashtext(?))", LOCK_NAMESPACE, conversation);
    }

    /**
     * Retires the conversation's ISSUED quotes that are not newer than {@code requestedAt}: EXPIRED when
     * past their validity, else SUPERSEDED; their handles are revoked.
     */
    private void retireOlderIssued(String conversation, Timestamp requestedAt) {
        List<Long> retired = jdbc.queryForList("""
                update quote
                set status = case when valid_until <= ? then 'EXPIRED' else 'SUPERSEDED' end,
                    row_version = row_version + 1
                where conversation_id = ? and status = 'ISSUED' and issued_at <= ?
                returning id
                """, Long.class, requestedAt, conversation, requestedAt);
        for (Long id : retired) {
            jdbc.update("update quote_handle set revoked_at = ? where quote_id = ? and revoked_at is null",
                    requestedAt, id);
        }
    }

    @Override
    public void recordNotQuoted(ConversationId conversation, NotQuotedReason reason, int lineCount, Instant now) {
        ownTransaction.executeWithoutResult(status -> {
            Timestamp at = Timestamp.from(now);
            lock(conversation.value());
            // A-33: a NOT_QUOTED request retires the older ISSUED quote; a newer one (that won a race
            // against this request) stays.
            retireOlderIssued(conversation.value(), at);
            jdbc.update("""
                    insert into quote_request (conversation_id, outcome, reason_code, line_count, occurred_at)
                    values (?, 'NOT_QUOTED', ?, ?, ?)
                    """, conversation.value(), reason.name(), lineCount, at);
        });
    }

    @Override
    public IssueResult issue(NewQuote quote, Limits limits) {
        return ownTransaction.execute(status -> {
            String conversation = quote.conversation().value();
            Timestamp now = Timestamp.from(quote.issuedAt());
            lock(conversation);
            // The newest snapshot wins: a request that started before the current ISSUED quote was
            // issued does not replace it (EP-06 backend review M-1).
            Integer newer = jdbc.queryForObject(
                    "select count(*) from quote where conversation_id = ? and status = 'ISSUED' and issued_at > ?",
                    Integer.class, conversation, now);
            if (newer != null && newer > 0) {
                return IssueResult.STALE;
            }
            // A-34 again under the lock: a request racing a NOT_QUOTED or the issue limit loses.
            Optional<NotQuotedReason> blocked = blocked(conversation, limits);
            if (blocked.isPresent()) {
                return blocked.get() == NotQuotedReason.HANDED_OFF ? IssueResult.LOCKED : IssueResult.LIMIT;
            }
            retireOlderIssued(conversation, now);
            Long quoteId = jdbc.queryForObject("""
                    insert into quote (public_ref, conversation_id, customer_digest, version, status, currency,
                                       money_scale, rounding_mode, total, issued_at, valid_until)
                    values (?, ?, ?, 1, 'ISSUED', ?, ?, ?, ?, ?, ?)
                    returning id
                    """, Long.class, quote.publicRef(), conversation, quote.customerDigest(),
                    quote.currency().getCurrencyCode(), quote.moneyScale(), quote.rounding().name(),
                    quote.calculation().total().amount(), now, Timestamp.from(quote.validUntil()));
            int lineNo = 1;
            for (QuoteCalculator.CalculatedLine line : quote.calculation().lines()) {
                jdbc.update("""
                        insert into quote_line (quote_id, line_no, sku, quantity, unit_price, line_total,
                                                price_source, price_revision)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """, quoteId, lineNo++, line.line().sku().value(), line.line().quantity(),
                        line.line().price().unitPrice().amount(), line.lineTotal().amount(),
                        line.line().price().priceSource(), line.line().price().revision());
            }
            jdbc.update("""
                    insert into quote_handle (token_hash, quote_id, quote_version, conversation_id, customer_digest,
                                              issued_at, expires_at)
                    values (?, ?, 1, ?, ?, ?, ?)
                    """, quote.handleHash(), quoteId, conversation, quote.customerDigest(), now,
                    Timestamp.from(quote.validUntil()));
            jdbc.update("""
                    insert into quote_request (conversation_id, outcome, line_count, quote_id, occurred_at)
                    values (?, 'ISSUED', ?, ?, ?)
                    """, conversation, quote.calculation().lines().size(), quoteId, now);
            return IssueResult.ISSUED;
        });
    }

    @Override
    public Optional<StoredHandle> findHandle(String tokenHash) {
        return jdbc.query("""
                select q.id, q.public_ref, q.version, h.quote_version, q.status,
                       h.conversation_id as handle_conversation, q.conversation_id as quote_conversation,
                       h.customer_digest as handle_customer, q.customer_digest as quote_customer,
                       h.expires_at, h.revoked_at, q.valid_until, q.currency, q.money_scale, q.total
                from quote_handle h join quote q on q.id = h.quote_id
                where h.token_hash = ?
                """, (rs, row) -> toHandle(rs), tokenHash).stream().findFirst();
    }

    private static StoredHandle toHandle(ResultSet rs) throws SQLException {
        // A handle bound to a different conversation or customer than its quote is foreign (defence in
        // depth, security review L-4).
        String conversation = rs.getString("handle_conversation").equals(rs.getString("quote_conversation"))
                ? rs.getString("handle_conversation") : "";
        String customer = rs.getString("handle_customer").equals(rs.getString("quote_customer"))
                ? rs.getString("handle_customer") : "";
        Timestamp revoked = rs.getTimestamp("revoked_at");
        return new StoredHandle(rs.getLong("id"), rs.getString("public_ref"), rs.getInt("version"),
                rs.getInt("quote_version"), rs.getString("status"), conversation, customer,
                rs.getTimestamp("expires_at").toInstant(), revoked == null ? null : revoked.toInstant(),
                rs.getTimestamp("valid_until").toInstant(), Currency.getInstance(rs.getString("currency")),
                rs.getInt("money_scale"), rs.getBigDecimal("total"));
    }

    @Override
    public List<StoredLine> lines(long quoteId) {
        return jdbc.query("""
                select sku, quantity, unit_price, line_total from quote_line where quote_id = ? order by line_no
                """, (rs, row) -> new StoredLine(rs.getString("sku"), rs.getInt("quantity"),
                rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_total")), quoteId);
    }
}
