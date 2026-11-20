package com.secureerp.agent.order.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.TestcontainersConfiguration;
import com.secureerp.agent.erp.internal.MockErpConnector;
import com.secureerp.agent.erp.internal.MockErpSimulation;
import com.secureerp.agent.identity.IdentityFixtures;
import com.secureerp.agent.identity.spi.ErpCustomerLink;
import com.secureerp.agent.order.DraftOrderSubmission;
import com.secureerp.agent.order.DraftOrders;
import com.secureerp.agent.platform.Money;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * EP-09c (A-41(l), A-46 decision 2A) against PostgreSQL with the real scheduler: a request nobody reads turns
 * into an unknown outcome on its own after the submit timeout, once, without an ERP call.
 */
@SpringBootTest(properties = {
        "apas.order.sweeper.enabled=true",
        "apas.order.sweeper.interval=PT0.2S",
        "apas.order.submit-timeout=PT1S"})
@Import(TestcontainersConfiguration.class)
// The context runs a 0.2 s sweeper: close it after this class instead of keeping it in the cache (backend L1).
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DraftOrderSweeperIT {

    private static final String QUOTE = "BG-SWEEPAAA";
    private static final String UNREAD_QUOTE = "BG-SWEEPBBB";

    @Autowired
    DraftOrders orders;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockErpConnector mockErp;

    @Autowired
    ApplicationContext context;

    private MockErpSimulation erp;

    @BeforeEach
    void clean() {
        jdbc.update("delete from draft_order_event");
        jdbc.update("delete from draft_order_request");
        erp = mockErp.simulation();
        erp.reset();
    }

    private static DraftOrderSubmission submission(String quoteRef) {
        return new DraftOrderSubmission(quoteRef, 1, new ErpCustomerLink("SYNTH-CUST-A"),
                List.of(new DraftOrderSubmission.Line("SKU-BRK-PAD-A", 2,
                        Money.of(new BigDecimal("555444333"), Currency.getInstance("VND")))));
    }

    private String status(String quoteRef) {
        return jdbc.queryForObject("select status from draft_order_request where quote_ref = ?", String.class,
                quoteRef);
    }

    @Test
    void anUnreadRequestBecomesUnknownWithoutAReader() throws InterruptedException {
        assertThat(context.getBeansOfType(DraftOrderSweeper.class)).hasSize(1);
        transactions.executeWithoutResult(tx -> {
            orders.register(submission(QUOTE), IdentityFixtures.TEAM_1);
            orders.register(submission(UNREAD_QUOTE), IdentityFixtures.TEAM_1);
        });
        assertThat(status(UNREAD_QUOTE)).isEqualTo("PENDING");

        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!"OUTCOME_UNKNOWN".equals(status(UNREAD_QUOTE)) && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }

        // Nobody called stateOf, unknown() or dispatch(): the sweeper did it.
        assertThat(status(UNREAD_QUOTE)).isEqualTo("OUTCOME_UNKNOWN");
        assertThat(status(QUOTE)).isEqualTo("OUTCOME_UNKNOWN");
        Thread.sleep(600); // a few more sweeps: the timeout is recorded once
        assertThat(jdbc.queryForObject("select count(*) from draft_order_event where event = 'TIMED_OUT'",
                Integer.class)).isEqualTo(2);
        assertThat(erp.createCalls()).as("a late request is never sent").isZero();
    }
}
