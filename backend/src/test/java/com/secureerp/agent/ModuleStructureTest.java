package com.secureerp.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/**
 * ARCH-13 and module allowlists (ADR-001): Spring Modulith verifies there are no cycles, no access
 * to another module's internal types and no dependency outside each module's
 * {@code allowedDependencies} declared in its package-info.
 *
 * <p>Modulith only lists a module once it contains a type, so the documented module set is checked
 * through the package-info annotations: deleting or renaming one must fail the build.
 */
class ModuleStructureTest {

    /** docs/architecture/MODULE_BOUNDARIES.md §1 (bootstrap = root package). */
    static final List<String> DOCUMENTED_MODULES = List.of(
            "identity", "conversation", "agent", "gateway", "policy", "tools", "catalog", "quote", "order",
            "handoff", "audit", "securityinspector", "platform", "erp");

    private static final ApplicationModules MODULES = ApplicationModules.of(SecureErpAgentApplication.class);

    @Test
    void modulesAreVerified() {
        MODULES.verify();
    }

    @ParameterizedTest
    @FieldSource("DOCUMENTED_MODULES")
    void documentedModuleDeclaresItsAllowlist(String module) throws ClassNotFoundException {
        Class<?> packageInfo = Class.forName("com.secureerp.agent." + module + ".package-info");
        ApplicationModule annotation = packageInfo.getAnnotation(ApplicationModule.class);
        assertThat(annotation).as("@ApplicationModule on %s", module).isNotNull();
        assertThat(annotation.allowedDependencies()).as("%s may not depend on erp", module)
                .noneMatch(dep -> dep.startsWith("erp"));
    }

    @Test
    void auditAndPlatformStayAtTheBottomOfTheGraph() throws ClassNotFoundException {
        assertThat(allowed("platform")).isEmpty();
        assertThat(allowed("audit")).containsExactly("platform");
    }

    @Test
    void detectedModulesAreDocumented() {
        assertThat(MODULES.stream().map(m -> m.getIdentifier().toString()))
                .isNotEmpty()
                .allMatch(DOCUMENTED_MODULES::contains);
    }

    private static String[] allowed(String module) throws ClassNotFoundException {
        return Class.forName("com.secureerp.agent." + module + ".package-info")
                .getAnnotation(ApplicationModule.class).allowedDependencies();
    }
}
