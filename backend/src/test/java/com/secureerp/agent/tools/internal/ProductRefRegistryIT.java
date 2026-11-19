package com.secureerp.agent.tools.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.TestcontainersConfiguration;
import com.secureerp.agent.catalog.spi.Provenance;
import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.gateway.api.QuoteRequestResult;
import com.secureerp.agent.identity.CustomerLinkBinding;
import com.secureerp.agent.identity.spi.ErpCustomerLink;
import com.secureerp.agent.quote.QuoteOutcome;
import com.secureerp.agent.quote.QuoteRequest;
import com.secureerp.agent.quote.QuoteService;
import com.secureerp.agent.tools.ToolFixtures;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * EP-06 regression: about 28 % of random tokens contained a VIN-like run, so the model's own
 * {@code requestQuote} arguments were refused as T-TOOL-13 (TOOL_SCHEMA). Issued tokens must never
 * look like a VIN to the argument check.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProductRefRegistryIT {

    @Autowired
    ProductRefRegistry registry;

    @Autowired
    QuoteService quotes;

    @Autowired
    CustomerLinkBinding binding;

    @Test
    void issuedTokensNeverLookLikeAVin() {
        for (int i = 0; i < 500; i++) {
            String token = registry.issue(ToolFixtures.conversation(60), "SKU-BRK-PAD-A", Provenance.VERIFIED,
                    Instant.now());

            assertThat(ArgumentParser.containsFullVin(token)).as(token).isFalse();
            assertThat(ArgumentParser.parse(ToolName.REQUEST_QUOTE,
                    "{\"lines\":[{\"productRef\":\"" + token + "\",\"quantity\":1}]}", ToolLimits.DEFAULTS))
                    .isInstanceOf(ToolArguments.QuoteLines.class);
        }
    }

    /** Architecture review L3: real quote handles pass the real argument check and egress type. */
    @Test
    void realQuoteHandlesPassTheToolAndEgressChecks() {
        for (int i = 0; i < 40; i++) {
            // A fresh customer reference and conversation each: A-34 allows 5 quotes per customer per hour.
            QuoteOutcome outcome = quotes.request(new QuoteRequest(ToolFixtures.conversation(100 + i),
                    binding.bind(new ErpCustomerLink("SYNTH-CUST-A")),
                    List.of(new QuoteRequest.Line(new Sku("SKU-AIR-FLT-A"), 1))));
            String handle = ((QuoteOutcome.Issued) outcome).quoteHandle();

            assertThat(ArgumentParser.containsFullVin(handle)).as(handle).isFalse();
            assertThat(new QuoteRequestResult(QuoteRequestResult.Status.ISSUED, handle, 1).quoteHandle())
                    .isEqualTo(handle);
        }
    }
}
