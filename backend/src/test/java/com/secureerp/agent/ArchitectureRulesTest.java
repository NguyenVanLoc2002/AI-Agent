package com.secureerp.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Evidence for ARCH-01..12 (MODULE_BOUNDARIES.md §5; ARCH-05/06/08 added in SEC-01a as part of
 * T-SEC01-SCHEMA): production code passes every
 * rule, and each rule demonstrably fails on a deliberate-violation fixture
 * ({@code com.secureerp.archfixture}) while not flagging the compliant fixture classes.
 */
class ArchitectureRulesTest {

    private static final String FIXTURE_BASE = "com.secureerp.archfixture";

    private static JavaClasses production;
    private static JavaClasses fixtures;

    @BeforeAll
    static void importClasses() {
        production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.secureerp.agent");
        fixtures = new ClassFileImporter().importPackages(FIXTURE_BASE, "com.openai.archfixture");
    }

    @Test
    @DisplayName("production code satisfies all implemented ARCH rules")
    void productionCodeSatisfiesRules() {
        assertThat(production.size()).as("production classes imported").isPositive();
        ArchitectureRules.forBasePackage("com.secureerp.agent").values().forEach(rule -> rule.check(production));
    }

    @Test
    @DisplayName("production code does not contain fixture or test classes")
    void productionImportExcludesFixtures() {
        assertThat(production.stream().map(c -> c.getName()))
                .noneMatch(name -> name.startsWith(FIXTURE_BASE) || name.endsWith("Test"));
    }

    @Test
    @DisplayName("ARCH-05/06 are not vacuous: production egress types exist and are checked")
    void egressRulesCoverProductionTypes() {
        assertThat(production.stream().map(c -> c.getName()))
                .contains("com.secureerp.agent.gateway.api.ModelRequest",
                        "com.secureerp.agent.gateway.api.TechnicalContext",
                        "com.secureerp.agent.gateway.api.ToolResultDto");
        long egressFields = production.stream()
                .filter(c -> c.getPackageName().startsWith("com.secureerp.agent.gateway.api"))
                .flatMap(c -> c.getFields().stream())
                .filter(f -> !f.getModifiers().contains(JavaModifier.STATIC))
                .count();
        assertThat(egressFields).isGreaterThanOrEqualTo(20);
    }

    static Stream<Arguments> violations() {
        String f = FIXTURE_BASE + ".";
        return Stream.of(
                Arguments.of("ARCH-01", f + "agent.internal.DirectProviderCall", f + "gateway.adapter.ProviderAdapter"),
                Arguments.of("ARCH-02a", f + "tools.internal.LeakyPricingTool", f + "quote.internal.QuotePricing"),
                Arguments.of("ARCH-02b", f + "agent.internal.DirectOrderSubmit", f + "order.internal.OrderSubmission"),
                Arguments.of("ARCH-03", f + "quote.internal.QuoteUsesErpAdapter", f + "erp.MockAritoAdapter"),
                Arguments.of("ARCH-04", f + "tools.internal.ToolCallsAgent", f + "conversation.internal.ConversationUsesAgent"),
                Arguments.of("ARCH-07", f + "quote.QuoteRowInApi", f + "quote.internal.QuoteRow"),
                Arguments.of("ARCH-07", f + "order.DraftOrderRowInApi", f + "quote.internal.QuoteRow"),
                Arguments.of("ARCH-09", f + "quote.spi.CostLeakingPort", f + "quote.spi.PricingPort"),
                Arguments.of("ARCH-09", f + "order.spi.CustomerSearchPort.searchCustomers", f + "order.spi.DraftOrderPort"),
                Arguments.of("ARCH-09", f + "order.spi.CustomerSearchPort.overrideUnitPrice", f + "erp.MockAritoAdapter"),
                Arguments.of("ARCH-10", f + "audit.internal.AuditReadsQuote", f + "audit.internal.CompliantAuditSink"),
                Arguments.of("ARCH-11", f + "tools.internal.ReflectiveToolInvoker", f + "tools.internal.TypedToolInvoker"),
                Arguments.of("ARCH-11", f + "tools.internal.ConstructorToolFactory", f + "tools.internal.TypedToolInvoker"),
                Arguments.of("ARCH-12", f + "catalog.internal.CatalogUsesStockPort", f + "catalog.internal.CatalogUsesBucket"),
                Arguments.of("ARCH-05", f + "gateway.api.LeakyPriceDto.unitPrice", f + "gateway.api.CompliantDto"),
                Arguments.of("ARCH-05", f + "gateway.api.LeakyPriceDto.total", f + "gateway.api.CompliantItem"),
                Arguments.of("ARCH-05", f + "gateway.api.MapCarrierDto.extra", f + "gateway.api.CompliantDto"),
                Arguments.of("ARCH-05", f + "gateway.api.MapCarrierDto.prices", f + "gateway.api.CompliantDto"),
                Arguments.of("ARCH-05", f + "gateway.api.MapCarrierDto.payload", f + "gateway.api.CompliantDto"),
                Arguments.of("ARCH-05", f + "gateway.api.NestedForeignDto.row", f + "gateway.api.CompliantDto"),
                Arguments.of("ARCH-05", f + "gateway.api.CustomerRefDto.customer", f + "gateway.api.CompliantDto"),
                Arguments.of("ARCH-06", f + "gateway.api.UnclassifiedDto", f + "gateway.api.CompliantDto"),
                Arguments.of("ARCH-06", f + "gateway.api.InternalClassifiedDto", f + "gateway.api.CompliantItem"),
                Arguments.of("ARCH-08a", f + "quote.internal.ForgesCustomerRef", f + "identity.internal.CustomerLinkResolver"),
                Arguments.of("ARCH-08d", f + "tools.internal.ReadsErpCustomerLink", f + "erp.PricesByErpLink"),
                Arguments.of("ARCH-08d", f + "tools.internal.ReadsErpCustomerLinkByReference", f + "erp.PricesByErpLink"),
                Arguments.of("ARCH-08e", f + "tools.internal.ChecksCustomerLink", f + "quote.internal.ResolvesCustomerLink"),
                Arguments.of("ARCH-08f", f + "quote.internal.BindsCustomer", f + "identity.internal.CustomerLinkResolver"),
                Arguments.of("ARCH-13a", f + "tools.internal.RendersQuoteCard", f + "conversation.internal.ShowsQuoteCard"),
                Arguments.of("ARCH-13b", f + "tools.internal.PricesInTools", f + "quote.internal.TotalsInQuote"),
                Arguments.of("ARCH-13c", f + "quote.internal.QuoteSearchesCatalog", f + "quote.internal.QuoteUsesSku"),
                Arguments.of("ARCH-13d", f + "tools.internal.ReadsStaffQuote", f + "conversation.internal.ShowsStaffQuote"),
                Arguments.of("ARCH-08b", f + "conversation.adapter.web.CustomerRefController.chat",
                        f + "conversation.adapter.web.SafeChatController"),
                Arguments.of("ARCH-08b", f + "conversation.adapter.web.CustomerIdController.quote",
                        f + "conversation.adapter.web.SafeChatController"),
                // Added after the SEC-01a architecture/security reviews (bypass routes).
                Arguments.of("ARCH-05", f + "gateway.api.ProductRefLike.quantity", f + "gateway.api.TechnicalContext"),
                Arguments.of("ARCH-05b", f + "gateway.api.InheritingDto", f + "gateway.api.CompliantDto"),
                Arguments.of("ARCH-05b", f + "gateway.api.OpenEgressInterface", f + "gateway.api.CompliantStatus"),
                Arguments.of("ARCH-05b", f + "gateway.api.PlainClassDto", f + "gateway.api.CompliantItem"),
                Arguments.of("ARCH-05c", f + "gateway.api.JsonPropertyDto", f + "gateway.api.CompliantDto"),
                Arguments.of("ARCH-06", f + "gateway.api.LookalikeClassifiedDto", f + "gateway.api.CompliantDto"),
                Arguments.of("ARCH-08b", f + "conversation.adapter.web.CustomerHeaderController.chat",
                        f + "conversation.adapter.web.SafeChatController"),
                Arguments.of("ARCH-08b", f + "conversation.adapter.web.MetaAnnotatedController.chat",
                        f + "conversation.adapter.web.SafeChatController"),
                Arguments.of("ARCH-08c", f + "conversation.adapter.web.ChatRequest.customer",
                        f + "conversation.adapter.web.SafeChatRequest"),
                Arguments.of("ARCH-08c", f + "conversation.adapter.web.ChatRequestWithId.customerId",
                        f + "conversation.adapter.web.SafeChatRequest"),
                // Added after the second SEC-01a review.
                Arguments.of("ARCH-05d", f + "gateway.api.LooseHandleDto.quoteHandle", f + "gateway.api.ProductRef"),
                Arguments.of("ARCH-05e", f + "gateway.api.ClassTypedDto", f + "gateway.api.NameTypedDto"),
                Arguments.of("ARCH-08b", f + "conversation.adapter.web.CustomerNumberController.chatByNumber",
                        f + "conversation.adapter.web.SafeChatController"),
                Arguments.of("ARCH-08b", f + "conversation.adapter.web.CustomerNumberController.chatByAccount",
                        f + "conversation.adapter.web.SafeChatController"),
                Arguments.of("ARCH-08b", f + "conversation.adapter.web.HeaderMapController.chat",
                        f + "conversation.adapter.web.SafeChatController"),
                Arguments.of("ARCH-08b", f + "conversation.adapter.web.ServletRequestController.chat",
                        f + "conversation.adapter.web.SafeChatController"),
                Arguments.of("ARCH-08b", f + "conversation.api.OrderEnvelope$Buyer.buyerId",
                        f + "conversation.adapter.web.SafeChatController"),
                // Added after the third SEC-01a review.
                Arguments.of("ARCH-05e", f + "gateway.api.FieldTypedDto", f + "gateway.api.NameTypedDto"),
                Arguments.of("ARCH-05e", f + "gateway.api.PropertyTypedDto", f + "gateway.api.NameTypedDto"),
                Arguments.of("ARCH-08b", f + "conversation.adapter.web.RenamedBodyController.byProperty",
                        f + "conversation.adapter.web.SafeChatController"),
                Arguments.of("ARCH-08b", f + "conversation.adapter.web.RenamedBodyController.byAlias",
                        f + "conversation.adapter.web.SafeChatController"),
                Arguments.of("ARCH-08b", f + "conversation.adapter.web.RawBodyController.buyers",
                        f + "conversation.adapter.web.SafeChatController"),
                Arguments.of("ARCH-08b", f + "conversation.adapter.web.RawBodyController.tree",
                        f + "conversation.adapter.web.SafeChatController"),
                Arguments.of("ARCH-08b", f + "conversation.adapter.web.RawBodyController.bytes",
                        f + "conversation.adapter.web.SafeChatController"),
                // EP-04 (decision 2026-11-19, instead of A-22 for now).
                Arguments.of("ARCH-14", f + "conversation.internal.StoresModelOutput",
                        f + "agent.internal.ValidatesModelOutput"));
    }

    @Test
    @DisplayName("review L3: no production class receives captured egress bodies")
    void noProductionEgressCaptureListener() {
        ArchRuleDefinition.noClasses()
                .should().implement("com.secureerp.agent.gateway.adapter.EgressCaptureListener")
                .as("EgressCaptureListener is a test-only hook (ADR-016 capture a)")
                .check(production);
    }

    @ParameterizedTest(name = "{0} catches {1}")
    @MethodSource("violations")
    void ruleDetectsDeliberateViolation(String ruleId, String violatingClass, String compliantClass) {
        // Second review L4: a renamed or deleted compliant fixture must not make the check vacuous.
        assertThat(fixtures.contain(compliantClass)).as("compliant fixture %s exists", compliantClass).isTrue();
        Map<String, ArchRule> rules = ArchitectureRules.forBasePackage(FIXTURE_BASE);
        ArchRule rule = rules.get(ruleId);
        assertThat(rule).as("rule %s exists", ruleId).isNotNull();

        EvaluationResult result = rule.evaluate(fixtures);
        String report = result.getFailureReport().toString();

        assertThat(result.hasViolation()).as("%s must fail on fixture", ruleId).isTrue();
        assertThat(report).contains(violatingClass);
        // The compliant class may appear as a dependency target, but never as the violating origin.
        assertThat(report)
                .doesNotContain("Class <" + compliantClass + ">")
                .doesNotContain("Method <" + compliantClass + ".")
                .doesNotContain("Field <" + compliantClass + ".");
        assertThatThrownBy(() -> rule.check(fixtures)).isInstanceOf(AssertionError.class);
    }
}
