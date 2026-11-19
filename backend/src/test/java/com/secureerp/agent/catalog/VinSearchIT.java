package com.secureerp.agent.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.TestcontainersConfiguration;
import com.secureerp.agent.gateway.EgressScan;
import com.secureerp.agent.gateway.api.ProductRef;
import com.secureerp.agent.gateway.api.ProductSearchResult;
import com.secureerp.agent.gateway.api.Provenance;
import com.secureerp.agent.gateway.api.ToolErrorResult;
import com.secureerp.agent.gateway.api.ToolProposal;
import com.secureerp.agent.policy.InboundSanitizer;
import com.secureerp.agent.tools.ToolCallContext;
import com.secureerp.agent.tools.ToolFixtures;
import com.secureerp.agent.tools.ToolManager;
import com.secureerp.agent.tools.ToolOutcome;
import com.secureerp.agent.tools.spi.ConversationAccessPort;
import com.secureerp.agent.tools.spi.ConversationAccessPort.Access;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * EP-05 against PostgreSQL (Flyway V5): VIN intake → {@code conversation_vin} → tool
 * {@code {"vinRef":"current"}} → Mock ERP fitment. UC-02 AC-02.1, T-UC02-VIN, T-UC02-PROVENANCE (tool
 * level), T-TOOL-13 (the VIN is never a tool argument and never in the result).
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@Import({TestcontainersConfiguration.class, VinSearchIT.ConversationAccess.class})
class VinSearchIT {

    private static final String VIN = "5ZZAPAS11RA000001";
    private static final String OTHER_VIN = "RLZAPAS20RA000002";
    private static final ToolProposal SEARCH_VIN = new ToolProposal("searchVerifiedProduct", "{\"vinRef\":\"current\"}");

    @TestConfiguration
    static class ConversationAccess {

        @Bean
        ConversationAccessPort conversationAccess() {
            return ToolFixtures.ownedByCustomerA();
        }
    }

    @Autowired
    VehicleContext vehicles;

    @Autowired
    ToolManager tools;

    @Autowired
    InboundSanitizer sanitizer;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from tool_invocation");
        jdbc.update("delete from product_ref");
        jdbc.update("delete from conversation_vin");
    }

    private static ToolErrorResult.Code code(ToolOutcome outcome) {
        return outcome.result() instanceof ToolErrorResult error ? error.code() : null;
    }

    @Test
    void vinFromIntakeDrivesTheVinRefSearch(CapturedOutput output) {
        ToolCallContext context = ToolFixtures.customer("alice", 21, 1);

        assertThat(vehicles.register(context.conversation(), "So khung: " + VIN))
                .isEqualTo(new VinIntake.Accepted("5ZZ**************"));
        ToolOutcome outcome = tools.execute(context, SEARCH_VIN);

        ProductSearchResult result = (ProductSearchResult) outcome.result();
        assertThat(result.candidates()).extracting(ProductRef::sku)
                .containsExactly("SKU-BRK-PAD-A", "SKU-BRK-PAD-B", "SKU-OIL-FLT-A");
        assertThat(result.candidates()).extracting(ProductRef::provenance).containsOnly(Provenance.CATALOG_LOOKUP);
        assertThat(result.toString()).doesNotContain(VIN);
        assertThat(EgressScan.containsSentinel(result.toString())).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from product_ref where conversation_id = ? and provenance = ?",
                Integer.class, context.conversation().value(), "CATALOG_LOOKUP")).isEqualTo(3);
        // Logs and the audit table carry no VIN (rule 10).
        assertThat(output.getAll()).doesNotContain(VIN);
        assertThat(jdbc.queryForList("select tool_name || coalesce(reason_code, '') from tool_invocation", String.class))
                .noneMatch(row -> row.contains(VIN));
    }

    @Test
    void aNewerVinReplacesTheOldOne() {
        ToolCallContext context = ToolFixtures.customer("alice", 22, 1);
        vehicles.register(context.conversation(), VIN);
        vehicles.register(context.conversation(), OTHER_VIN);

        ProductSearchResult result = (ProductSearchResult) tools.execute(context, SEARCH_VIN).result();

        assertThat(result.candidates()).extracting(ProductRef::sku).containsExactly("SKU-AIR-FLT-A");
        assertThat(jdbc.queryForObject("select count(*) from conversation_vin where conversation_id = ?", Integer.class,
                context.conversation().value())).isEqualTo(1);
    }

    /** The VIN is bound to its conversation: another conversation of the same principal has none. */
    @Test
    void anotherConversationCannotSearchWithThisVin() {
        vehicles.register(ToolFixtures.conversation(23), VIN);

        assertThat(code(tools.execute(ToolFixtures.customer("alice", 24, 1), SEARCH_VIN)))
                .isEqualTo(ToolErrorResult.Code.TOOL_UNAVAILABLE);
        assertThat(code(tools.execute(ToolFixtures.customer("bob", 25, 2), SEARCH_VIN)))
                .isEqualTo(ToolErrorResult.Code.TOOL_UNAVAILABLE);
    }

    /** AC-02.1: an invalid VIN asks again and retires the previous VIN, so no stale vehicle is searched. */
    @Test
    void invalidOrAmbiguousVinClearsTheCurrentVin() {
        ToolCallContext context = ToolFixtures.customer("alice", 26, 1);
        vehicles.register(context.conversation(), VIN);

        assertThat(vehicles.register(context.conversation(), "VIN 5ZZAPAS10RA000001"))
                .isEqualTo(new VinIntake.Invalid());
        assertThat(code(tools.execute(context, SEARCH_VIN))).isEqualTo(ToolErrorResult.Code.TOOL_UNAVAILABLE);

        vehicles.register(context.conversation(), VIN);
        assertThat(vehicles.register(context.conversation(), VIN + " " + OTHER_VIN)).isEqualTo(new VinIntake.Multiple());
        assertThat(code(tools.execute(ToolFixtures.customer("alice", 26, 2), SEARCH_VIN)))
                .isEqualTo(ToolErrorResult.Code.TOOL_UNAVAILABLE);
    }

    @Test
    void anExpiredVinIsNotUsed() {
        ToolCallContext context = ToolFixtures.customer("alice", 27, 1);
        vehicles.register(context.conversation(), VIN);
        Instant past = Instant.now().minusSeconds(3 * 3600);
        jdbc.update("update conversation_vin set registered_at = ?, expires_at = ? where conversation_id = ?",
                Timestamp.from(past), Timestamp.from(past.plusSeconds(7200)), context.conversation().value());

        assertThat(code(tools.execute(context, SEARCH_VIN))).isEqualTo(ToolErrorResult.Code.TOOL_UNAVAILABLE);
    }

    /**
     * Parity (EP-05 reviews: security M-1, architecture M3): whenever the inbound sanitizer masks a
     * VIN for the model, intake does not answer None, so the model never sees a masked new vehicle
     * while an older VIN stays current.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        VIN, "vin 5zzapas11ra000001", "VIN-" + VIN, "5ZZAP-AS11R-A000001", "5ZZAP AS11R A000001",
        "5ZZAP.AS11R.A000001", "5ZZ APAS1 1RA000001", "5Z-ZA-PA-S1-1R-A0-00001", "5ZZAP  AS11R. A000001",
        "xe 5ZZAP AS11R A000001 va " + OTHER_VIN, "MA PHANH 5ZZAP AS11R A000001", "so khung: 5ZZAPAS10RA000001",
        "ABCDEFGH 12345 6789", "x_" + VIN
    })
    void whenTheSanitizerMasksAVinIntakeSeesOne(String message) {
        InboundSanitizer.Result sanitized = sanitizer.sanitize(message);
        boolean masked = sanitized instanceof InboundSanitizer.Sanitized s
                && s.flags().contains(InboundSanitizer.RedactionFlag.VIN_MASKED);

        VinIntake intake = vehicles.register(ToolFixtures.conversation(30), message);

        if (message.equals(VIN) || message.equals("5ZZAP AS11R A000001")) {
            assertThat(masked).as("oracle check: the sanitizer masks these").isTrue();
        }
        if (masked) {
            assertThat(intake).as("sanitizer masked a VIN, intake must not ignore it").isNotEqualTo(new VinIntake.None());
        }
    }

    /** Security M-1 scenario: a new VIN typed in groups replaces the stored one. */
    @Test
    void aNewVinTypedInGroupsReplacesTheStoredVin() {
        ToolCallContext context = ToolFixtures.customer("alice", 31, 1);
        vehicles.register(context.conversation(), VIN);

        assertThat(vehicles.register(context.conversation(), "xe khac: RLZAP AS20R A000002"))
                .isEqualTo(new VinIntake.Accepted("RLZ**************"));
        ProductSearchResult result = (ProductSearchResult) tools.execute(context, SEARCH_VIN).result();
        assertThat(result.candidates()).extracting(ProductRef::sku).containsExactly("SKU-AIR-FLT-A");
    }

    /** Reviews L-4/L5: expired rows (full VINs) are deleted at the next intake, not only hidden. */
    @Test
    void expiredRowsArePurged() {
        vehicles.register(ToolFixtures.conversation(32), VIN);
        Instant past = Instant.now().minusSeconds(3 * 3600);
        jdbc.update("update conversation_vin set registered_at = ?, expires_at = ? where conversation_id = ?",
                Timestamp.from(past), Timestamp.from(past.plusSeconds(7200)), ToolFixtures.conversation(32).value());

        vehicles.register(ToolFixtures.conversation(33), "khong co so khung");

        assertThat(jdbc.queryForObject("select count(*) from conversation_vin", Integer.class)).isZero();
    }

    /**
     * A-31 end to end: a category + vehicle-model result is CATALOG_LOOKUP and cannot be quoted; an
     * exact OE result for the same SKU is VERIFIED and passes the productRef check.
     */
    @Test
    void onlyExactOeResultsPassTheQuoteCheck() {
        ToolCallContext context = ToolFixtures.customer("alice", 34, 1);
        ProductSearchResult byModel = (ProductSearchResult) tools.execute(context, new ToolProposal(
                "searchVerifiedProduct", "{\"category\":\"Ma phanh truoc\",\"vehicleModel\":\"Vios 2019\"}")).result();
        ProductSearchResult byOe = (ProductSearchResult) tools.execute(ToolFixtures.customer("alice", 34, 2),
                new ToolProposal("searchVerifiedProduct", "{\"oe\":\"04465-0K290\"}")).result();

        assertThat(byModel.candidates()).isNotEmpty().extracting(ProductRef::provenance)
                .containsOnly(Provenance.CATALOG_LOOKUP);
        assertThat(byOe.candidates()).extracting(ProductRef::provenance).containsExactly(Provenance.VERIFIED);
        assertThat(code(tools.execute(ToolFixtures.customer("alice", 34, 3), quote(byModel))))
                .isEqualTo(ToolErrorResult.Code.FORBIDDEN_RESOURCE);
        // Ownership and VERIFIED pass, and the quote is issued (EP-06; no error code).
        assertThat(code(tools.execute(ToolFixtures.customer("alice", 34, 4), quote(byOe)))).isNull();
    }

    private static ToolProposal quote(ProductSearchResult result) {
        return new ToolProposal("requestQuote",
                "{\"lines\":[{\"productRef\":\"" + result.candidates().getFirst().productRef() + "\",\"quantity\":1}]}");
    }

    /** T-TOOL-13 end to end: the model cannot pass the VIN itself. */
    @Test
    void theModelCannotPassAVinAsAnArgument() {
        ToolCallContext context = ToolFixtures.customer("alice", 28, 1);

        assertThat(code(tools.execute(context, new ToolProposal("searchVerifiedProduct", "{\"vinRef\":\"" + VIN + "\"}"))))
                .isEqualTo(ToolErrorResult.Code.TOOL_SCHEMA);
        assertThat(code(tools.execute(context, new ToolProposal("searchVerifiedProduct", "{\"oe\":\"" + VIN + "\"}"))))
                .isEqualTo(ToolErrorResult.Code.TOOL_SCHEMA);
    }

    /** The table refuses a value that is not a well-formed VIN (defence in depth below the type). */
    @Test
    void theTableRejectsMalformedVins() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into conversation_vin (conversation_id, vin, registered_at, expires_at)
                values (?, ?, now(), now() + interval '1 hour')
                """, ToolFixtures.conversation(29).value(), "5ZZAPAS11RA00000O"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
