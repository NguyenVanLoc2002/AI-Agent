package com.secureerp.agent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.secureerp.agent.policy.internal.DefaultEgressPolicy;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * ARCH-xx rules from docs/architecture/MODULE_BOUNDARIES.md §3, parameterised by base package so
 * the same rule objects run against production code and against deliberate-violation fixtures.
 *
 * <p>ARCH-09/11 are denylists and therefore incomplete by nature (backend-reviewer L2); the type-level
 * egress boundary (ARCH-05/06) is the primary control. ARCH-01 cannot detect a raw HTTP client
 * calling a provider; network egress control is ADR-016.
 *
 * <p>Implemented in POC-001: ARCH-01, 02, 03, 04, 07, 09, 10, 11, 12 (ARCH-13 = Modulith verify in
 * {@code ModuleStructureTest}). Added in SEC-01a: ARCH-05 (egress DTO field types, as an allowlist),
 * ARCH-06 ({@code @EgressClassified(APPROVED_TECHNICAL)}), ARCH-08 ({@code AuthenticatedCustomerRef}
 * construction and controller parameters). Marker types are matched by simple name so the same
 * rules run against the fixtures.
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

        String[] egress = egressPackages(base);

        rules.put("ARCH-05", fields()
                .that().areDeclaredInClassesThat().resideInAnyPackage(egress)
                .and().areNotStatic()
                .should(haveEgressSafeType(base))
                .as("ARCH-05 egress DTO fields use only String, boolean, enums, egress types or allowlisted "
                        + "quantity numbers (no money, IDs, Map, Object, JsonNode, entities)")
                .allowEmptyShould(true));

        rules.put("ARCH-05b", classes()
                .that().resideInAnyPackage(egress)
                .and(NOT_PACKAGE_INFO)
                .should(beClosedEgressShape())
                .as("ARCH-05 egress types are records, enums, sealed interfaces or final static-only utilities "
                        + "(no inheritance, no open interfaces, no plain DTO classes)")
                .allowEmptyShould(true));

        rules.put("ARCH-05c", noClasses()
                .that().resideInAnyPackage(egress)
                .should().dependOnClassesThat(FORBIDDEN_SERIALIZATION_TYPES)
                .as("ARCH-05 egress types use no Jackson annotations except @JsonTypeInfo/@JsonSubTypes")
                .allowEmptyShould(true));

        rules.put("ARCH-05d", fields()
                .that().areDeclaredInClassesThat().resideInAnyPackage(egress)
                .and().areNotStatic()
                .should(beApprovedIfServerIdentifier(base))
                .as("ARCH-05 fields named like server identifiers (honeytoken check only at egress) exist only "
                        + "on the approved, strictly validated Type.field pairs")
                .allowEmptyShould(true));

        rules.put("ARCH-05e", classes()
                .that().resideInAnyPackage(egress)
                .should(useOnlyLogicalTypeNames())
                .as("ARCH-05 @JsonTypeInfo on egress types and their fields uses Id.NAME with property \"type\" "
                        + "(no Java class names, no type ID under a scan-exempt field name)")
                .allowEmptyShould(true));

        rules.put("ARCH-06", classes()
                .that().resideInAnyPackage(egress)
                .and(RECORD_OR_INTERFACE)
                .should().beAnnotatedWith(APPROVED_TECHNICAL_CLASSIFICATION)
                .as("ARCH-06 every egress record/interface carries @EgressClassified(APPROVED_TECHNICAL)")
                .allowEmptyShould(true));

        rules.put("ARCH-08a", noClasses()
                .that().resideOutsideOfPackage(base + ".identity..")
                .should().callConstructorWhere(DescribedPredicate.describe("AuthenticatedCustomerRef constructor",
                        call -> call.getTargetOwner().getSimpleName().equals(CUSTOMER_REF)))
                .as("ARCH-08 only identity creates AuthenticatedCustomerRef")
                .allowEmptyShould(true));

        rules.put("ARCH-08b", methods()
                .that().areDeclaredInClassesThat().areMetaAnnotatedWith(simpleNameIn("RestController", "Controller"))
                .should(notAcceptCustomerIdentityFromRequest(base))
                .as("ARCH-08 controllers take no AuthenticatedCustomerRef, no customer/tenant/account/user ID "
                        + "parameter or nested body field, no header/parameter map and no raw request")
                .allowEmptyShould(true));

        rules.put("ARCH-08c", fields()
                .that().areDeclaredInClassesThat().resideInAPackage(base + "..adapter.web..")
                .should(notCarryCustomerIdentity())
                .as("ARCH-08 web request/response types carry no AuthenticatedCustomerRef and no customerId/tenantId field")
                .allowEmptyShould(true));

        return rules;
    }

    /** Packages whose types are serialized towards the model provider (ADR-003). */
    static String[] egressPackages(String base) {
        return new String[] {base + ".gateway.api..", base + ".tools.api.dto.."};
    }

    /**
     * ARCH-05: numbers are allowed only for these exact {@code Type.field} pairs (MODULE_BOUNDARIES §3,
     * narrowed after architecture review M5: a bare name would let any record add e.g. a stock
     * {@code quantity}).
     */
    static final Set<String> NUMERIC_FIELD_ALLOWLIST =
            Set.of("gateway.api.TechnicalContext.requestedQuantity", "gateway.api.QuoteRequestResult.lineCount");

    static final String EGRESS_CLASSIFIED = "com.secureerp.agent.platform.EgressClassified";

    /**
     * ARCH-05d: the only {@code Type.field} pairs that may use a name from
     * {@link DefaultEgressPolicy#SERVER_IDENTIFIER_FIELDS}; each validates its value strictly (UUID,
     * prompt ID, masked VIN, or 22-character server token). Pairs (like the numeric allowlist) are
     * relative to the base package, so a same-named record elsewhere does not inherit the exemption
     * (third review L3).
     */
    static final Set<String> SERVER_IDENTIFIER_FIELD_ALLOWLIST = Set.of(
            "gateway.api.ModelRequest.traceId", "gateway.api.ModelRequest.systemPromptId",
            "gateway.api.TechnicalContext.maskedVin", "gateway.api.TechnicalContext.vinRef",
            "gateway.api.TechnicalContext.quoteHandle", "gateway.api.ProductRef.productRef",
            "gateway.api.QuoteRequestResult.quoteHandle");

    /** ARCH-05e: the only type-ID property; it is not a scan-exempt field name. */
    private static final Set<String> TYPE_ID_PROPERTIES = Set.of("type", "@type");

    /** ARCH-08b: server-side principal parameters, exempt from the name and body checks (third review M1). */
    private static final Set<String> PRINCIPAL_ANNOTATIONS = Set.of(
            "org.springframework.security.core.annotation.AuthenticationPrincipal",
            "org.springframework.security.core.annotation.CurrentSecurityContext");
    private static final List<String> PRINCIPAL_TYPES =
            List.of("java.security.Principal", "org.springframework.security.core.Authentication");

    /** ARCH-08b: handler-adjacent methods that never bind a client request body or parameter. */
    private static final Set<String> NON_HANDLER_METHOD_ANNOTATIONS = Set.of("ExceptionHandler", "InitBinder");

    /** Jackson annotations that rename a body field on the wire (third review M2). */
    private static final Set<String> JSON_NAME_ANNOTATIONS =
            Set.of("com.fasterxml.jackson.annotation.JsonProperty", "com.fasterxml.jackson.annotation.JsonAlias");

    /**
     * ARCH-08b: controller parameter types that expose the whole request (every header or parameter),
     * so a customer ID could be read without a named parameter (second review M4).
     */
    private static final List<String> RAW_REQUEST_TYPES = List.of(
            "java.util.Map", "org.springframework.util.MultiValueMap", "org.springframework.http.HttpHeaders",
            "jakarta.servlet.ServletRequest", "org.springframework.web.context.request.WebRequest",
            "org.springframework.http.HttpEntity", "org.springframework.http.server.ServerHttpRequest",
            // raw bodies the framework does not bind to typed fields (third review M2)
            "java.io.InputStream", "java.io.Reader", "tools.jackson.databind.JsonNode",
            "com.fasterxml.jackson.databind.JsonNode");

    private static final Set<String> SMALL_INTEGER_TYPES = Set.of("int", "java.lang.Integer");
    private static final Set<String> SAFE_SCALAR_TYPES = Set.of("java.lang.String", "boolean", "java.lang.Boolean");
    private static final Set<String> SAFE_CONTAINERS = Set.of("java.util.List", "java.util.Set", "java.util.Optional");
    private static final String CUSTOMER_REF = "AuthenticatedCustomerRef";
    private static final Pattern CUSTOMER_PARAMETER =
            Pattern.compile("(?i)^(x[_-])?(customer|tenant|cust|account|acct|buyer|user|khach[_-]?hang)"
                    + "([_-]?(id|ref|code|no|number|num|key))?$");

    private static final DescribedPredicate<JavaClass> NOT_PACKAGE_INFO =
            DescribedPredicate.describe("not package-info", c -> !c.getSimpleName().equals("package-info"));

    private static final Set<String> ALLOWED_JACKSON_ANNOTATIONS = Set.of(
            "com.fasterxml.jackson.annotation.JsonTypeInfo", "com.fasterxml.jackson.annotation.JsonSubTypes",
            // value type of @JsonTypeInfo.requireTypeIdForSubtypes (default member), not a property hook
            "com.fasterxml.jackson.annotation.OptBoolean");

    private static final DescribedPredicate<JavaClass> FORBIDDEN_SERIALIZATION_TYPES = DescribedPredicate.describe(
            "Jackson types other than @JsonTypeInfo/@JsonSubTypes", c -> {
                String name = c.getName();
                if (name.startsWith("tools.jackson.")) {
                    return true;
                }
                return name.startsWith("com.fasterxml.jackson.")
                        && ALLOWED_JACKSON_ANNOTATIONS.stream().noneMatch(a -> name.equals(a) || name.startsWith(a + "$"));
            });

    private static final DescribedPredicate<JavaClass> RECORD_OR_INTERFACE = DescribedPredicate.describe(
            "records or interfaces", c -> c.isInterface() && !c.isAnnotation() && !c.getSimpleName().equals("package-info")
                    || c.getRawSuperclass().map(s -> s.getName().equals("java.lang.Record")).orElse(false));

    private static final DescribedPredicate<JavaAnnotation<?>> APPROVED_TECHNICAL_CLASSIFICATION =
            DescribedPredicate.describe("@EgressClassified(APPROVED_TECHNICAL)",
                    a -> a.getRawType().getName().equals(EGRESS_CLASSIFIED)
                            && a.get("value").map(v -> v instanceof JavaEnumConstant e
                                    && e.name().equals("APPROVED_TECHNICAL")).orElse(false));

    /**
     * ARCH-05 as an allowlist (stricter than the documented denylist): a field type must be String,
     * boolean, a List/Set/Optional of safe types, a type declared in an egress package, or a small
     * integer whose field name is allowlisted. Generic type arguments are checked recursively.
     */
    private static ArchCondition<JavaField> haveEgressSafeType(String base) {
        return new ArchCondition<>("have an egress-safe type") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                Optional<String> problem = unsafe(field.getType(), relativePair(field, base), base);
                problem.ifPresent(p -> events.add(SimpleConditionEvent.violated(field,
                        "Field <" + field.getFullName() + "> " + p)));
            }
        };
    }

    private static Optional<String> unsafe(JavaType type, String ownerAndField, String base) {
        if (!(type instanceof JavaClass) && !(type instanceof JavaParameterizedType)) {
            return Optional.of("uses a wildcard or type variable " + type.getName());
        }
        JavaClass raw = type.toErasure();
        String name = raw.getName();
        if (SAFE_SCALAR_TYPES.contains(name)) {
            return Optional.empty();
        }
        if (SMALL_INTEGER_TYPES.contains(name)) {
            return NUMERIC_FIELD_ALLOWLIST.contains(ownerAndField)
                    ? Optional.empty()
                    : Optional.of("is numeric but not in the numeric allowlist " + NUMERIC_FIELD_ALLOWLIST);
        }
        if (SAFE_CONTAINERS.contains(name)) {
            if (!(type instanceof JavaParameterizedType parameterized)) {
                return Optional.of("uses raw container " + name);
            }
            for (JavaType argument : parameterized.getActualTypeArguments()) {
                Optional<String> nested = unsafe(argument, ownerAndField, base);
                if (nested.isPresent()) {
                    return nested;
                }
            }
            return Optional.empty();
        }
        boolean inEgressPackage = name.startsWith(base + ".gateway.api.") || name.startsWith(base + ".tools.api.dto.");
        if (inEgressPackage && !raw.getSimpleName().equals(CUSTOMER_REF)) {
            return Optional.empty();
        }
        return Optional.of("has non-allowlisted type " + name);
    }

    /**
     * ARCH-05b: closes the bypasses found in review (H3): inherited fields from a foreign superclass,
     * open interfaces implemented elsewhere, and plain classes. Allowed: record, enum, sealed
     * interface, or a final class with only static fields, static methods and private constructors.
     */
    private static ArchCondition<JavaClass> beClosedEgressShape() {
        return new ArchCondition<>("be a closed egress shape") {
            @Override
            public void check(JavaClass c, ConditionEvents events) {
                String superclass = c.getRawSuperclass().map(JavaClass::getName).orElse("java.lang.Object");
                boolean ok;
                if (c.isEnum() || superclass.equals("java.lang.Record")) {
                    ok = true;
                } else if (c.isInterface()) {
                    ok = !c.isAnnotation() && isSealed(c);
                } else {
                    ok = superclass.equals("java.lang.Object")
                            && c.getModifiers().contains(JavaModifier.FINAL)
                            && c.getFields().stream().allMatch(f -> f.getModifiers().contains(JavaModifier.STATIC))
                            && c.getMethods().stream().allMatch(m -> m.getModifiers().contains(JavaModifier.STATIC))
                            && c.getConstructors().stream()
                                    .allMatch(k -> k.getModifiers().contains(JavaModifier.PRIVATE));
                }
                if (!ok) {
                    events.add(SimpleConditionEvent.violated(c, "Class <" + c.getName()
                            + "> is not a record, enum, sealed interface or static-only utility (superclass "
                            + superclass + ")"));
                }
            }
        };
    }

    private static boolean isSealed(JavaClass c) {
        try {
            return c.reflect().isSealed();
        } catch (RuntimeException | LinkageError e) {
            return false; // cannot prove it is sealed: fail closed
        }
    }

    private static ArchCondition<JavaField> notCarryCustomerIdentity() {
        return new ArchCondition<>("not carry customer identity") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                boolean refType = field.getRawType().getSimpleName().equals(CUSTOMER_REF)
                        || (field.getType() instanceof JavaParameterizedType p && p.getActualTypeArguments().stream()
                                .anyMatch(a -> a.toErasure().getSimpleName().equals(CUSTOMER_REF)));
                if (refType || CUSTOMER_PARAMETER.matcher(field.getName()).matches()) {
                    events.add(SimpleConditionEvent.violated(field,
                            "Field <" + field.getFullName() + "> carries customer identity in a web type"));
                }
            }
        };
    }

    /** {@code Owner.field} relative to the base package, e.g. {@code gateway.api.ProductRef.productRef}. */
    private static String relativePair(JavaField field, String base) {
        String owner = field.getOwner().getName();
        return (owner.startsWith(base + ".") ? owner.substring(base.length() + 1) : owner) + "." + field.getName();
    }

    private static ArchCondition<JavaField> beApprovedIfServerIdentifier(String base) {
        return new ArchCondition<>("be an approved server-identifier field") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                String pair = relativePair(field, base);
                if (DefaultEgressPolicy.SERVER_IDENTIFIER_FIELDS.contains(field.getName())
                        && !SERVER_IDENTIFIER_FIELD_ALLOWLIST.contains(pair)) {
                    events.add(SimpleConditionEvent.violated(field, "Field <" + field.getFullName()
                            + "> uses a server-identifier name outside " + SERVER_IDENTIFIER_FIELD_ALLOWLIST));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> useOnlyLogicalTypeNames() {
        return new ArchCondition<>("use @JsonTypeInfo(use = Id.NAME) only") {
            @Override
            public void check(JavaClass c, ConditionEvents events) {
                List<JavaAnnotation<?>> annotations = new ArrayList<>(c.getAnnotations());
                c.getFields().forEach(f -> annotations.addAll(f.getAnnotations()));
                annotations.stream()
                        .filter(a -> a.getRawType().getName().equals("com.fasterxml.jackson.annotation.JsonTypeInfo"))
                        .filter(a -> !a.get("use").map(v -> v instanceof JavaEnumConstant e && e.name().equals("NAME"))
                                .orElse(false)
                                || !TYPE_ID_PROPERTIES.contains(a.get("property").map(String::valueOf).orElse("@type")))
                        .forEach(a -> events.add(SimpleConditionEvent.violated(c, "Class <" + c.getName()
                                + "> uses @JsonTypeInfo without Id.NAME or with a type property other than \"type\"")));
            }
        };
    }

    private static ArchCondition<JavaMethod> notAcceptCustomerIdentityFromRequest(String base) {
        return new ArchCondition<>("not accept customer identity from the request") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (method.getAnnotations().stream()
                        .anyMatch(a -> NON_HANDLER_METHOD_ANNOTATIONS.contains(a.getRawType().getSimpleName()))) {
                    return; // exception handlers and binders do not bind client input (third review L5)
                }
                List<JavaParameter> parameters = method.getParameters();
                Parameter[] reflected = reflectParameters(method);
                for (int i = 0; i < parameters.size(); i++) {
                    JavaParameter parameter = parameters.get(i);
                    if (parameter.getRawType().getSimpleName().equals(CUSTOMER_REF)) {
                        events.add(SimpleConditionEvent.violated(method,
                                "Method <" + method.getFullName() + "> takes " + CUSTOMER_REF + " from the request"));
                    }
                    if (isServerSidePrincipal(parameter)) {
                        continue; // identity from the security context, the pattern CLAUDE.md requires
                    }
                    if (parameter.getRawType().getName().equals("[B")) {
                        events.add(SimpleConditionEvent.violated(method,
                                "Method <" + method.getFullName() + "> takes a raw byte[] body"));
                    }
                    for (String rawType : RAW_REQUEST_TYPES) {
                        if (parameter.getRawType().isAssignableTo(rawType)) {
                            events.add(SimpleConditionEvent.violated(method, "Method <" + method.getFullName()
                                    + "> takes the raw request or a header/parameter map (" + rawType + ")"));
                        }
                    }
                    reachableIdentityField(parameter.getType(), base, new HashSet<>()).ifPresent(path ->
                            events.add(SimpleConditionEvent.violated(method, "Method <" + method.getFullName()
                                    + "> accepts customer identity in its body via " + path)));
                    for (JavaAnnotation<?> annotation : parameter.getAnnotations()) {
                        for (String attribute : List.of("value", "name")) {
                            annotation.get(attribute).map(String::valueOf)
                                    .filter(v -> CUSTOMER_PARAMETER.matcher(v).matches())
                                    .ifPresent(v -> events.add(SimpleConditionEvent.violated(method,
                                            "Method <" + method.getFullName() + "> binds request parameter " + v)));
                        }
                    }
                    if (reflected != null && reflected[i].isNamePresent()
                            && CUSTOMER_PARAMETER.matcher(reflected[i].getName()).matches()) {
                        events.add(SimpleConditionEvent.violated(method,
                                "Method <" + method.getFullName() + "> has parameter " + reflected[i].getName()));
                    }
                }
            }
        };
    }

    /**
     * ARCH-08b (second review M4): a request body type declared anywhere in the code base, and every
     * type reachable through its fields and generic arguments, carries no customer identity field.
     */
    private static boolean isServerSidePrincipal(JavaParameter parameter) {
        return parameter.getAnnotations().stream().anyMatch(a -> PRINCIPAL_ANNOTATIONS.contains(a.getRawType().getName()))
                || PRINCIPAL_TYPES.stream().anyMatch(t -> parameter.getRawType().isAssignableTo(t));
    }

    /** Wire names set by {@code @JsonProperty} / {@code @JsonAlias} on a body field. */
    private static List<String> jsonNames(JavaField field) {
        List<String> names = new ArrayList<>();
        for (JavaAnnotation<?> annotation : field.getAnnotations()) {
            if (JSON_NAME_ANNOTATIONS.contains(annotation.getRawType().getName())) {
                annotation.get("value").ifPresent(value -> {
                    if (value instanceof Object[] array) {
                        Arrays.stream(array).map(String::valueOf).forEach(names::add);
                    } else {
                        names.add(String.valueOf(value));
                    }
                });
            }
        }
        return names;
    }

    private static Optional<String> reachableIdentityField(JavaType type, String base, Set<String> visited) {
        JavaClass raw = type.toErasure();
        if (raw.isArray()) {
            // Arrays are followed to their element type (third review M2: Buyer[] was skipped).
            return reachableIdentityField(raw.getComponentType(), base, visited);
        }
        if (type instanceof JavaParameterizedType parameterized) {
            for (JavaType argument : parameterized.getActualTypeArguments()) {
                Optional<String> nested = reachableIdentityField(argument, base, visited);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        if (!raw.getName().startsWith(base + ".") || !visited.add(raw.getName())) {
            return Optional.empty();
        }
        for (JavaField field : raw.getAllFields()) {
            if (field.getModifiers().contains(JavaModifier.STATIC)) {
                continue;
            }
            if (field.getRawType().getSimpleName().equals(CUSTOMER_REF)
                    || CUSTOMER_PARAMETER.matcher(field.getName()).matches()
                    || jsonNames(field).stream().anyMatch(n -> CUSTOMER_PARAMETER.matcher(n).matches())) {
                return Optional.of(field.getFullName());
            }
            Optional<String> nested = reachableIdentityField(field.getType(), base, visited);
            if (nested.isPresent()) {
                return nested;
            }
        }
        return Optional.empty();
    }

    private static Parameter[] reflectParameters(JavaMethod method) {
        try {
            return method.reflect().getParameters();
        } catch (RuntimeException | LinkageError e) {
            return null; // name check is the secondary layer; type/annotation checks still apply
        }
    }

    private static DescribedPredicate<JavaAnnotation<?>> simpleNameIn(String... simpleNames) {
        Set<String> names = Set.of(simpleNames);
        return DescribedPredicate.describe("@" + names,
                annotation -> names.contains(annotation.getRawType().getSimpleName()));
    }
}
