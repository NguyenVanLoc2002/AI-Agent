package com.secureerp.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Evidence for ARCH-01..04, 07, 09..12 (MODULE_BOUNDARIES.md §5): production code passes every
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
                Arguments.of("ARCH-12", f + "catalog.internal.CatalogUsesStockPort", f + "catalog.internal.CatalogUsesBucket"));
    }

    @ParameterizedTest(name = "{0} catches {1}")
    @MethodSource("violations")
    void ruleDetectsDeliberateViolation(String ruleId, String violatingClass, String compliantClass) {
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
                .doesNotContain("Method <" + compliantClass + ".");
        assertThatThrownBy(() -> rule.check(fixtures)).isInstanceOf(AssertionError.class);
    }
}
