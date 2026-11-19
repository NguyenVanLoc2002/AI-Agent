package com.secureerp.agent.tools.internal;

import com.secureerp.agent.catalog.spi.Provenance;
import com.secureerp.agent.gateway.api.EgressText;
import com.secureerp.agent.gateway.api.ProductRef;
import com.secureerp.agent.platform.ConversationId;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL implementation ({@code product_ref}, Flyway V4). Tokens carry 128 random bits. Issuing
 * runs in its own transaction: once the model has seen a token, a caller rollback must not remove it
 * (EP-03 architecture review M3).
 */
@Repository
class JdbcProductRefRegistry implements ProductRefRegistry {

    /** About 28 % of random tokens contain a VIN-like run; 64 draws make a failure negligible. */
    private static final int MAX_DRAWS = 64;

    private final JdbcTemplate jdbc;
    private final ToolLimits limits;
    private final TransactionTemplate ownTransaction;
    private final SecureRandom random = new SecureRandom();

    JdbcProductRefRegistry(JdbcTemplate jdbc, ToolLimits limits, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.limits = limits;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public String issue(ConversationId conversation, String sku, Provenance provenance, Instant now) {
        String token = newToken();
        ownTransaction.executeWithoutResult(status -> jdbc.update("""
                insert into product_ref (token, conversation_id, sku, provenance, issued_at, expires_at)
                values (?, ?, ?, ?, ?, ?)
                """, token, conversation.value(), sku, provenance.name(), Timestamp.from(now),
                Timestamp.from(now.plus(limits.productRefTtl()))));
        return token;
    }

    @Override
    public Optional<IssuedProductRef> resolve(ConversationId conversation, String token, Instant now) {
        return jdbc.query("""
                select token, sku, provenance from product_ref
                where token = ? and conversation_id = ? and expires_at > ?
                """, (rs, row) -> new IssuedProductRef(rs.getString("token"), rs.getString("sku"),
                Provenance.valueOf(rs.getString("provenance"))), token, conversation.value(), Timestamp.from(now))
                .stream().findFirst();
    }

    /**
     * A random token that satisfies the egress format (draws again on a 7-digit run) and does not
     * look like a VIN: otherwise the model's own {@code requestQuote} arguments would be refused as
     * T-TOOL-13 (EP-06 finding: about 28 % of quote calls failed with TOOL_SCHEMA).
     */
    private String newToken() {
        byte[] bytes = new byte[16];
        for (int draw = 0; draw < MAX_DRAWS; draw++) {
            random.nextBytes(bytes);
            String token = ProductRef.PRODUCT_REF_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (ArgumentParser.containsFullVin(token)) {
                continue;
            }
            try {
                return EgressText.serverToken(token, "productRef", ProductRef.PRODUCT_REF_PREFIX);
            } catch (IllegalArgumentException digitRun) {
                // draw again
            }
        }
        throw new IllegalStateException("could not draw a product reference token");
    }
}
