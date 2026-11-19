package com.secureerp.agent.quote.internal;

import com.secureerp.agent.platform.ConversationId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL implementation (Flyway V6). Writes run in their own transaction (READ COMMITTED) under
 * advisory locks ({@link #LOCK_NAMESPACE}) taken in a fixed order, customer then conversation, so that
 * concurrent requests are serialised: at most one ISSUED quote per conversation (the partial unique index
 * enforces the same rule), the newest snapshot wins, and the per-customer A-34 limits cannot be raced
 * past. A lock wait is bounded ({@code lock_timeout}); the waiter fails and the service answers
 * NOT_QUOTED (STORE_UNAVAILABLE), which neither locks nor retires anything.
 */
@Repository
class JdbcQuoteStore implements QuoteStore {

    /** Advisory lock namespace for quote writes (tools uses 30_003). */
    static final int LOCK_NAMESPACE = 30_006;

    /** Business refusals that lock the customer (A-34, owner 2026-11-19). */
    private static final String LOCKING_REASONS = Arrays.stream(NotQuotedReason.values())
            .filter(NotQuotedReason::locksAndRetires)
            .map(reason -> "'" + reason.name() + "'")
            .collect(Collectors.joining(", "));
    private static final String LOCKED_SINCE = """
            select count(*) from quote_request where customer_digest = ? and outcome = 'NOT_QUOTED'
              and reason_code in (%s) and occurred_at > ?
            """.formatted(LOCKING_REASONS);
    /** A lost race (STALE_REQUEST) passed the ERP checks, so it counts toward the limit (security review 2 L-3). */
    private static final String ISSUED_SINCE = """
            select count(*) from quote_request where customer_digest = ?
              and (outcome = 'ISSUED' or reason_code = 'STALE_REQUEST') and occurred_at > ?
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate ownTransaction;

    JdbcQuoteStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.ownTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public Optional<NotQuotedReason> gate(String customerDigest, Limits limits) {
        return customerDigest == null ? Optional.empty() : blocked(customerDigest, limits);
    }

    private Optional<NotQuotedReason> blocked(String customerDigest, Limits limits) {
        if (count(LOCKED_SINCE, customerDigest, limits.lockSince()) > 0) {
            return Optional.of(NotQuotedReason.HANDED_OFF);
        }
        if (count(ISSUED_SINCE, customerDigest, limits.issuedSince()) >= limits.maxIssued()) {
            return Optional.of(NotQuotedReason.REISSUE_LIMIT);
        }
        return Optional.empty();
    }

    private int count(String sql, String key, Instant since) {
        Integer count = jdbc.queryForObject(sql, Integer.class, key, Timestamp.from(since));
        return count == null ? 0 : count;
    }

    /** Customer first, then conversation: one order everywhere, so two writers cannot deadlock. */
    private void lock(String customerDigest, String conversation) {
        lock(jdbc, customerDigest, conversation);
    }

    /** The same locks for every quote write, so a confirmation and a new quote cannot race (A-36f, EP-07). */
    static void lock(JdbcTemplate jdbc, String customerDigest, String conversation) {
        jdbc.execute("set local lock_timeout = '5s'");
        if (customerDigest != null) {
            jdbc.queryForList("select pg_advisory_xact_lock(?, hashtext(?))", LOCK_NAMESPACE, "c:" + customerDigest);
        }
        jdbc.queryForList("select pg_advisory_xact_lock(?, hashtext(?))", LOCK_NAMESPACE, "v:" + conversation);
    }

    /**
     * Retires this customer's ISSUED quotes in the conversation that are not newer than
     * {@code requestedAt}: EXPIRED when past their validity, else SUPERSEDED; their handles are revoked.
     * Another customer's quote is never touched (security review 2 L-1).
     */
    private void retireOlderIssued(String conversation, String customerDigest, Timestamp requestedAt) {
        List<Long> retired = jdbc.queryForList("""
                update quote
                set status = case when valid_until <= ? then 'EXPIRED' else 'SUPERSEDED' end,
                    row_version = row_version + 1
                where conversation_id = ? and customer_digest = ? and status = 'ISSUED' and issued_at <= ?
                returning id
                """, Long.class, requestedAt, conversation, customerDigest, requestedAt);
        for (Long id : retired) {
            jdbc.update("update quote_handle set revoked_at = ? where quote_id = ? and revoked_at is null",
                    requestedAt, id);
        }
    }

    @Override
    public void recordNotQuoted(ConversationId conversation, String customerDigest, NotQuotedReason reason,
            int lineCount, Instant now) {
        ownTransaction.executeWithoutResult(status -> {
            Timestamp at = Timestamp.from(now);
            lock(customerDigest, conversation.value());
            // A-33 (refined 2026-11-19): only a business refusal retires the older ISSUED quote; a newer
            // one (that won a race against this request) stays.
            if (reason.locksAndRetires() && customerDigest != null) {
                retireOlderIssued(conversation.value(), customerDigest, at);
            }
            jdbc.update("""
                    insert into quote_request (conversation_id, outcome, reason_code, customer_digest, line_count,
                                               occurred_at)
                    values (?, 'NOT_QUOTED', ?, ?, ?, ?)
                    """, conversation.value(), reason.name(), customerDigest, lineCount, at);
        });
    }

    @Override
    public IssueResult issue(NewQuote quote, Limits limits) {
        return ownTransaction.execute(status -> {
            String conversation = quote.conversation().value();
            String customer = quote.customerDigest();
            Timestamp now = Timestamp.from(quote.issuedAt());
            lock(customer, conversation);
            Integer foreign = jdbc.queryForObject("""
                    select count(*) from quote where conversation_id = ? and status = 'ISSUED' and customer_digest <> ?
                    """, Integer.class, conversation, customer);
            if (foreign != null && foreign > 0) {
                return IssueResult.FOREIGN;
            }
            // The newest snapshot wins: a request that started before the current ISSUED quote was
            // issued does not replace it (EP-06 backend review M-1).
            Integer newer = jdbc.queryForObject(
                    "select count(*) from quote where conversation_id = ? and status = 'ISSUED' and issued_at > ?",
                    Integer.class, conversation, now);
            if (newer != null && newer > 0) {
                return IssueResult.STALE;
            }
            // A-34 again under the locks: a request racing a business refusal or the issue limit loses.
            Optional<NotQuotedReason> blocked = blocked(customer, limits);
            if (blocked.isPresent()) {
                return blocked.get() == NotQuotedReason.HANDED_OFF ? IssueResult.LOCKED : IssueResult.LIMIT;
            }
            retireOlderIssued(conversation, customer, now);
            Long quoteId = jdbc.queryForObject("""
                    insert into quote (public_ref, conversation_id, customer_digest, version, status, currency,
                                       money_scale, rounding_mode, total, issued_at, valid_until)
                    values (?, ?, ?, 1, 'ISSUED', ?, ?, ?, ?, ?, ?)
                    returning id
                    """, Long.class, quote.publicRef(), conversation, customer,
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
                    """, quote.handleHash(), quoteId, conversation, customer, now,
                    Timestamp.from(quote.validUntil()));
            jdbc.update("""
                    insert into quote_request (conversation_id, outcome, customer_digest, line_count, quote_id,
                                               occurred_at)
                    values (?, 'ISSUED', ?, ?, ?, ?)
                    """, conversation, customer, quote.calculation().lines().size(), quoteId, now);
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
