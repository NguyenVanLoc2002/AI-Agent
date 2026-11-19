package com.secureerp.agent.quote.internal;

import com.secureerp.agent.platform.ConversationId;
import com.secureerp.agent.quote.QuoteStaffRequired;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persistence of the confirmation flow (EP-07; Flyway V6 and V10). Every read filters by the
 * customer digest (rule 20: ownership on every row access). Every status change:
 * <ul>
 *   <li>is allowed by {@link QuoteStatus#canBecome};</li>
 *   <li>is a conditional update on the expected current status (a concurrent change makes it a no-op
 *       the caller sees);</li>
 *   <li>when it leaves ISSUED, runs under the same advisory locks as issuing a quote
 *       ({@link JdbcQuoteStore#lock}), so a confirmation and a new quote in the conversation are
 *       serialised (A-36f).</li>
 * </ul>
 * A change that needs staff publishes {@link QuoteStaffRequired} inside its transaction: the
 * conversation lock and the handoff record commit with it or not at all.
 */
@Repository
class ConfirmationStore {

    /** {@code toString()} is redacted: amounts, digests and hashes never reach a log line. */
    record QuoteRow(long id, String publicRef, int version, QuoteStatus status, String conversationId,
            String customerDigest, Instant validUntil, Currency currency, int moneyScale, RoundingMode rounding,
            BigDecimal total, String confirmTokenHash) {

        @Override
        public String toString() {
            return "QuoteRow[" + status + ", redacted]";
        }
    }

    record LineRow(String sku, int quantity, BigDecimal unitPrice, BigDecimal lineTotal, String priceSource,
            String priceRevision) {

        @Override
        public String toString() {
            return "LineRow[redacted]";
        }
    }

    /** Outcome of one confirmation attempt, stored in {@code quote_confirmation}. */
    enum Attempt {
        /** Admitted and counted, outcome not recorded yet. */
        STARTED,
        NOT_FOUND,
        STALE_VERSION,
        EXPIRED,
        SOURCE_UNAVAILABLE,
        BLOCKED,
        REPRICED,
        ACCEPTED,
        REPLAY,
        /** The quote changed, or a lock was not obtained, while the attempt ran. */
        CONFLICT
    }

    /** What {@link #mirror} did. */
    enum Mirrored {
        CHANGED,
        /** The quote already is in the target status (or further along while the order is still being sent). */
        UNCHANGED,
        /** No quote has this reference and version. */
        NO_QUOTE,
        /** The quote is in a status that cannot become the target: quote and order disagree. */
        REFUSED
    }

    private static final String COLUMNS = "id, public_ref, version, status, conversation_id, customer_digest, "
            + "valid_until, currency, money_scale, rounding_mode, total, confirm_token_hash";

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate ownTransaction;
    private final TransactionTemplate joined;

    ConfirmationStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
            ApplicationEventPublisher events, Clock clock) {
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.ownTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.joined = new TransactionTemplate(transactionManager);
    }

    Optional<QuoteRow> find(String publicRef, int version, String customerDigest) {
        return jdbc.query("select " + COLUMNS + " from quote where public_ref = ? and version = ? "
                + "and customer_digest = ?", (rs, row) -> toRow(rs), publicRef, version, customerDigest)
                .stream().findFirst();
    }

    /** The highest version of the customer's quote. */
    Optional<QuoteRow> latest(String publicRef, String customerDigest) {
        return jdbc.query("select " + COLUMNS + " from quote where public_ref = ? and customer_digest = ? "
                + "order by version desc limit 1", (rs, row) -> toRow(rs), publicRef, customerDigest)
                .stream().findFirst();
    }

    List<LineRow> lines(long quoteId) {
        return jdbc.query("""
                select sku, quantity, unit_price, line_total, price_source, price_revision
                from quote_line where quote_id = ? order by line_no
                """, (rs, row) -> new LineRow(rs.getString("sku"), rs.getInt("quantity"),
                rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_total"), rs.getString("price_source"),
                rs.getString("price_revision")), quoteId);
    }

    /**
     * Counts and reserves one attempt atomically, under the customer's advisory lock, before anything
     * else happens (TH-26): parallel requests cannot all pass the limit and reach the ERP.
     *
     * @return the attempt's id; empty when the customer already made {@code max} attempts since {@code since}
     */
    Optional<Long> admitAttempt(String customerDigest, Instant since, int max, Instant now) {
        return ownTransaction.execute(tx -> {
            jdbc.execute("set local lock_timeout = '5s'");
            jdbc.queryForList("select pg_advisory_xact_lock(?, hashtext(?))", JdbcQuoteStore.LOCK_NAMESPACE,
                    "c:" + customerDigest);
            Integer count = jdbc.queryForObject(
                    "select count(*) from quote_confirmation where customer_digest = ? and occurred_at > ?",
                    Integer.class, customerDigest, Timestamp.from(since));
            if (count != null && count >= max) {
                return Optional.<Long>empty();
            }
            return Optional.ofNullable(jdbc.queryForObject("""
                    insert into quote_confirmation (customer_digest, outcome, occurred_at)
                    values (?, 'STARTED', ?)
                    returning id
                    """, Long.class, customerDigest, Timestamp.from(now)));
        });
    }

    /** Records how an admitted attempt ended; the reason of a blocked one is for staff only (A-14). */
    void finishAttempt(long attemptId, Long quoteId, Attempt attempt, NotQuotedReason reason) {
        jdbc.update("update quote_confirmation set quote_id = ?, outcome = ?, reason_code = ? where id = ?",
                quoteId, attempt.name(), reason == null ? null : reason.name(), attemptId);
    }

    /** Stores the hash of the confirmation token of the card just rendered; only an ISSUED quote has one. */
    boolean armConfirmation(long quoteId, String tokenHash) {
        return jdbc.update("update quote set confirm_token_hash = ? where id = ? and status = 'ISSUED'",
                tokenHash, quoteId) == 1;
    }

    /** ISSUED → EXPIRED once the validity has passed. */
    boolean expire(QuoteRow row) {
        return Boolean.TRUE.equals(ownTransaction.execute(tx -> {
            JdbcQuoteStore.lock(jdbc, row.customerDigest(), row.conversationId());
            Instant now = clock.instant();
            boolean changed = jdbc.update(move(QuoteStatus.ISSUED, QuoteStatus.EXPIRED) + " and valid_until <= ?",
                    row.id(), Timestamp.from(now)) == 1;
            if (changed) {
                revokeHandles(row.id(), now);
            }
            return changed;
        }));
    }

    /**
     * ISSUED → CONFIRMATION_BLOCKED (BR-38). Like every business refusal it locks the customer for
     * automatic quotes (A-34) through a NOT_QUOTED row with the internal reason, and it needs staff.
     */
    boolean block(QuoteRow row, NotQuotedReason reason, int lineCount) {
        return Boolean.TRUE.equals(ownTransaction.execute(tx -> {
            JdbcQuoteStore.lock(jdbc, row.customerDigest(), row.conversationId());
            Instant now = clock.instant();
            if (jdbc.update(move(QuoteStatus.ISSUED, QuoteStatus.CONFIRMATION_BLOCKED), row.id()) != 1) {
                return false;
            }
            revokeHandles(row.id(), now);
            jdbc.update("""
                    insert into quote_request (conversation_id, outcome, reason_code, customer_digest, line_count,
                                               occurred_at)
                    values (?, 'NOT_QUOTED', ?, ?, ?, ?)
                    """, row.conversationId(), reason.name(), row.customerDigest(), lineCount, Timestamp.from(now));
            staffRequired(row.conversationId(), row.customerDigest(), QuoteStaffRequired.Reason.CONFIRMATION_BLOCKED);
            return true;
        }));
    }

    /**
     * BR-16: the old version becomes SUPERSEDED and the same quote reference gets version + 1, ISSUED,
     * with the new price snapshot and a new validity. The issued snapshot itself is never changed.
     * Times are read under the lock, after the re-check, not before it.
     */
    boolean reissue(QuoteRow row, QuoteCalculator.Calculation calculation, Duration validity) {
        return Boolean.TRUE.equals(ownTransaction.execute(tx -> {
            JdbcQuoteStore.lock(jdbc, row.customerDigest(), row.conversationId());
            Instant now = clock.instant();
            Instant validUntil = now.plus(validity);
            // A quote that expired while the ERP was asked is not given a new life as a new version.
            if (jdbc.update(move(QuoteStatus.ISSUED, QuoteStatus.SUPERSEDED) + " and valid_until > ?", row.id(),
                    Timestamp.from(now)) != 1) {
                return false;
            }
            revokeHandles(row.id(), now);
            Long quoteId = jdbc.queryForObject("""
                    insert into quote (public_ref, conversation_id, customer_digest, version, status, currency,
                                       money_scale, rounding_mode, total, issued_at, valid_until)
                    values (?, ?, ?, ?, 'ISSUED', ?, ?, ?, ?, ?, ?)
                    returning id
                    """, Long.class, row.publicRef(), row.conversationId(), row.customerDigest(), row.version() + 1,
                    row.currency().getCurrencyCode(), row.moneyScale(), row.rounding().name(),
                    calculation.total().amount(), Timestamp.from(now), Timestamp.from(validUntil));
            int lineNo = 1;
            for (QuoteCalculator.CalculatedLine line : calculation.lines()) {
                jdbc.update("""
                        insert into quote_line (quote_id, line_no, sku, quantity, unit_price, line_total,
                                                price_source, price_revision)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """, quoteId, lineNo++, line.line().sku().value(), line.line().quantity(),
                        line.line().price().unitPrice().amount(), line.lineTotal().amount(),
                        line.line().price().priceSource(), line.line().price().revision());
            }
            return true;
        }));
    }

    /**
     * ISSUED → CONFIRMED → ORDER_SUBMITTING in one transaction, only while the quote is still valid;
     * {@code register} writes the draft order request in the same transaction, so a confirmed quote
     * always has exactly one request (BR-17). False when the quote is no longer ISSUED and valid. The
     * validity is checked against the time under the lock, after the re-check: a quote that expired while
     * the ERP was asked is not confirmed.
     */
    boolean confirm(QuoteRow row, Runnable register) {
        return Boolean.TRUE.equals(ownTransaction.execute(tx -> {
            JdbcQuoteStore.lock(jdbc, row.customerDigest(), row.conversationId());
            Instant now = clock.instant();
            Timestamp at = Timestamp.from(now);
            boolean confirmed = jdbc.update("update quote set status = 'CONFIRMED', confirmed_at = ?, "
                    + "row_version = row_version + 1 where id = ? and status = 'ISSUED' and valid_until > ?",
                    at, row.id(), at) == 1;
            if (!confirmed) {
                return false;
            }
            if (jdbc.update(move(QuoteStatus.CONFIRMED, QuoteStatus.ORDER_SUBMITTING), row.id()) != 1) {
                throw new IllegalStateException("confirmed quote could not start submitting");
            }
            revokeHandles(row.id(), now);
            register.run();
            return true;
        }));
    }

    /**
     * Moves the quote to the mirror of the draft order state when a transition from its current
     * status exists. Joins the caller's transaction (reconciliation) or runs in its own.
     */
    Mirrored mirror(String publicRef, int version, QuoteStatus target) {
        String sources = QuoteStatus.sourcesOf(target).stream().map(status -> "'" + status.name() + "'")
                .collect(Collectors.joining(", "));
        if (sources.isEmpty()) {
            return Mirrored.REFUSED;
        }
        return joined.execute(tx -> {
            List<String[]> changed = jdbc.query("update quote set status = ?, row_version = row_version + 1 "
                    + "where public_ref = ? and version = ? and status in (" + sources + ") "
                    + "returning conversation_id, customer_digest",
                    (rs, row) -> new String[] {rs.getString(1), rs.getString(2)}, target.name(), publicRef, version);
            for (String[] quote : changed) {
                if (target == QuoteStatus.ORDER_OUTCOME_UNKNOWN) {
                    staffRequired(quote[0], quote[1], QuoteStaffRequired.Reason.ORDER_OUTCOME_UNKNOWN);
                } else if (target == QuoteStatus.ORDER_FAILED) {
                    staffRequired(quote[0], quote[1], QuoteStaffRequired.Reason.ORDER_FAILED);
                }
            }
            if (!changed.isEmpty()) {
                return Mirrored.CHANGED;
            }
            List<String> current = jdbc.queryForList("select status from quote where public_ref = ? and version = ?",
                    String.class, publicRef, version);
            if (current.isEmpty()) {
                return Mirrored.NO_QUOTE;
            }
            // "Still being sent" is never ahead of the quote: a later status simply stays.
            return current.getFirst().equals(target.name()) || target == QuoteStatus.ORDER_SUBMITTING
                    ? Mirrored.UNCHANGED : Mirrored.REFUSED;
        });
    }

    private void staffRequired(String conversation, String customerDigest, QuoteStaffRequired.Reason reason) {
        events.publishEvent(new QuoteStaffRequired(new ConversationId(conversation), customerDigest, reason));
    }

    /** A guarded status update by quote id; the single parameter of the returned SQL is the id. */
    private static String move(QuoteStatus from, QuoteStatus to) {
        if (!from.canBecome(to)) {
            throw new IllegalStateException("quote transition not allowed: " + from + " -> " + to);
        }
        return "update quote set status = '" + to.name() + "', row_version = row_version + 1 where id = ? "
                + "and status = '" + from.name() + "'";
    }

    private void revokeHandles(long quoteId, Instant now) {
        jdbc.update("update quote_handle set revoked_at = ? where quote_id = ? and revoked_at is null",
                Timestamp.from(now), quoteId);
    }

    private static QuoteRow toRow(ResultSet rs) throws SQLException {
        return new QuoteRow(rs.getLong("id"), rs.getString("public_ref"), rs.getInt("version"),
                QuoteStatus.valueOf(rs.getString("status")), rs.getString("conversation_id"),
                rs.getString("customer_digest"), rs.getTimestamp("valid_until").toInstant(),
                Currency.getInstance(rs.getString("currency")), rs.getInt("money_scale"),
                RoundingMode.valueOf(rs.getString("rounding_mode")), rs.getBigDecimal("total"),
                rs.getString("confirm_token_hash"));
    }
}
