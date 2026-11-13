package com.secureerp.agent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * ARCH-xx rules from docs/architecture/MODULE_BOUNDARIES.md §3, parameterised by base package so
 * the same rule objects run against production code and against deliberate-violation fixtures.
 *
 * <p>ARCH-09/11 are denylists and therefore incomplete by nature (backend-reviewer L2); the type-level
 * egress boundary (ARCH-05/06) is the primary control. ARCH-01 cannot detect a raw HTTP client
 * calling a provider; network egress control is ADR-016.
 *
 * <p>Implemented in POC-001: ARCH-01, 02, 03, 04, 07, 09, 10, 11, 12 (ARCH-13 = Modulith verify in
 * {@code ModuleStructureTest}). Deferred to SEC-01a because their marker types do not exist yet:
 * ARCH-05 (egress DTO field types), ARCH-06 ({@code @EgressClassified}), ARCH-08
 * ({@code AuthenticatedCustomerRef}).
 */
public final class ArchitectureRules {

    /** Known LLM provider SDK / client packages (ARCH-01). Extend when a provider is approved (D-07). */
    static final String[] PROVIDER_SDK_PACKAGES = {
        "com.openai..", "com.anthropic..", "dev.langchain4j..", "org.springframework.ai..",
        "com.google.genai..", "com.google.cloud.vertexai..", "com.azure.ai.openai..", "io.github.sashirestela.."
    };

    /** ARCH-09: capabilities an ERP port must never offer (rule 20). */
    static final String FORBIDDEN_ERP_METHOD = "(?i)^(.*cost.*|.*margin.*"
            + "|(list|find|search|get|getAll|query|export|enumerate)\\w*Customers.*"
            + "|(approve|release|confirm)\\w*Order.*|approve.*"
            + "|(update|set|change|modify|override)\\w*Price.*)$";

    private ArchitectureRules() {
    }

    public static Map<String, ArchRule> forBasePackage(String base) {
        Map<String, ArchRule> rules = new LinkedHashMap<>();

        rules.put("ARCH-01", noClasses()
                .that().resideOutsideOfPackage(base + ".gateway.adapter..")
                .should().dependOnClassesThat().resideInAnyPackage(PROVIDER_SDK_PACKAGES)
                .as("ARCH-01 only gateway.adapter may use a model provider SDK")
                .allowEmptyShould(true));

        rules.put("ARCH-02a", noClasses()
                .that().resideOutsideOfPackages(base + ".quote..", base + ".order..", base + ".erp..")
                .should().dependOnClassesThat().resideInAPackage(base + ".quote.spi..")
                .as("ARCH-02 only quote, order, erp may depend on quote.spi (pricing/stock/credit)")
                .allowEmptyShould(true));

        rules.put("ARCH-02b", noClasses()
                .that().resideOutsideOfPackages(base + ".order..", base + ".erp..")
                .should().dependOnClassesThat().resideInAPackage(base + ".order.spi..")
                .as("ARCH-02 only order, erp may depend on order.spi (draft order)")
                .allowEmptyShould(true));

        rules.put("ARCH-03", noClasses()
                .that().resideOutsideOfPackage(base + ".erp..")
                .and().resideInAPackage(base + "..")
                .and().doNotHaveFullyQualifiedName(base + ".SecureErpAgentApplication")
                .should().dependOnClassesThat().resideInAPackage(base + ".erp..")
                .as("ARCH-03 no module may depend on the erp adapter")
                .allowEmptyShould(true));

        rules.put("ARCH-04", noClasses()
                .that().resideOutsideOfPackages(base + ".agent..", base + ".conversation..")
                .should().dependOnClassesThat().resideInAPackage(base + ".agent..")
                .as("ARCH-04 only conversation may depend on agent")
                .allowEmptyShould(true));

        rules.put("ARCH-07", classes()
                .that().areAnnotatedWith(simpleNameIn("Entity", "Table"))
                .should().resideInAPackage(base + ".*.internal..")
                .as("ARCH-07 persistence entities (@Entity / Spring Data JDBC @Table) live only in <module>.internal")
                .allowEmptyShould(true));

        rules.put("ARCH-09", noMethods()
                .that().areDeclaredInClassesThat().resideInAnyPackage(base + "..spi..", base + ".erp..")
                .and().arePublic()
                .should().haveNameMatching(FORBIDDEN_ERP_METHOD)
                .as("ARCH-09 ERP ports/adapters expose no cost, customer listing, approval or price update")
                .allowEmptyShould(true));

        rules.put("ARCH-10", noClasses()
                .that().resideInAPackage(base + ".audit..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        base + ".conversation..", base + ".agent..", base + ".gateway..", base + ".policy..",
                        base + ".tools..", base + ".catalog..", base + ".quote..", base + ".order..",
                        base + ".handoff..", base + ".securityinspector..", base + ".erp..")
                .as("ARCH-10 audit does not depend on business modules")
                .allowEmptyShould(true));

        rules.put("ARCH-11", noClasses()
                .that().resideInAPackage(base + ".tools..")
                .should().callMethod(Method.class, "invoke", Object.class, Object[].class)
                .orShould().callMethod(Constructor.class, "newInstance", Object[].class)
                .orShould().dependOnClassesThat().resideInAPackage("java.lang.invoke..")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.util.ReflectionUtils")
                .as("ARCH-11 tools are not invoked via reflection")
                .allowEmptyShould(true));

        rules.put("ARCH-12", noClasses()
                .that().resideInAPackage(base + ".catalog..")
                .should().dependOnClassesThat().resideInAPackage(base + ".quote.spi..")
                .as("ARCH-12 catalog uses only the coarse AvailabilityBucketPort, never quote.spi")
                .allowEmptyShould(true));

        return rules;
    }

    private static DescribedPredicate<JavaAnnotation<?>> simpleNameIn(String... simpleNames) {
        Set<String> names = Set.of(simpleNames);
        return DescribedPredicate.describe("@" + names,
                annotation -> names.contains(annotation.getRawType().getSimpleName()));
    }
}
