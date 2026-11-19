package com.secureerp.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * Configuration guardrails (POC-001 req. 3, 8, 9; ADR-012, ADR-015): no committed credentials, no
 * real provider/ERP endpoints, actuator limited to health, errors never expose internals.
 */
class ConfigurationPolicyTest {

    private static Properties load(String resource) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(resource));
        Properties properties = factory.getObject();
        assertThat(properties).as(resource).isNotNull();
        return properties;
    }

    @Test
    void baseProfileHasNoDefaultCredentialsOrHosts() {
        Properties base = load("application.yml");
        assertThat(base.getProperty("spring.datasource.url")).isEqualTo("${APP_DB_URL}");
        assertThat(base.getProperty("spring.datasource.username")).isEqualTo("${APP_DB_USERNAME}");
        assertThat(base.getProperty("spring.datasource.password")).isEqualTo("${APP_DB_PASSWORD}");
    }

    /** EP-04, ADR-010/ADR-015 (decisions 2026-11-19): demo password from the environment only. */
    @Test
    void demoProfileTakesThePasswordFromTheEnvironmentAndSessionCookieIsSafe() {
        Properties demo = load("application-demo.yml");
        assertThat(demo.getProperty("apas.identity.demo-password")).isEqualTo("${APAS_DEMO_PASSWORD:}");
        assertThat(demo.stringPropertyNames()).noneMatch(name -> name.contains("password") && !name.equals(
                "apas.identity.demo-password"));
        Properties base = load("application.yml");
        assertThat(base.getProperty("apas.identity.demo-password")).isNull();
        assertThat(base.getProperty("spring.profiles.group.local")).isEqualTo("demo");
        assertThat(base.getProperty("server.servlet.session.cookie.http-only")).isEqualTo("true");
        assertThat(base.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
        assertThat(base.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
        assertThat(base.getProperty("server.servlet.session.timeout")).isEqualTo("30m");
        assertThat(base.getProperty("apas.identity.absolute-timeout")).isEqualTo("8h");
    }

    @Test
    void localProfileDoesNotCommitPassword() {
        Properties local = load("application-local.yml");
        assertThat(local.getProperty("spring.datasource.password")).isEqualTo("${APP_DB_PASSWORD}");
        assertThat(local.getProperty("server.address")).isEqualTo("127.0.0.1");
    }

    @Test
    void actuatorExposesOnlyHealthWithoutDetails() {
        Properties base = load("application.yml");
        assertThat(base.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(base.getProperty("management.endpoints.access.default")).isEqualTo("none");
        assertThat(base.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
    }

    @Test
    void errorsNeverExposeInternals() {
        Properties base = load("application.yml");
        assertThat(base.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(base.getProperty("server.error.include-message")).isEqualTo("never");
        assertThat(base.getProperty("server.error.include-path")).isEqualTo("never");
        assertThat(base.getProperty("spring.jackson.deserialization.fail-on-unknown-properties")).isEqualTo("true");
        assertThat(base.getProperty("spring.flyway.clean-disabled")).isEqualTo("true");
    }

    @Test
    void noRealProviderOrErpConfigurationInResources() throws IOException {
        List<Pattern> forbidden = Stream.of("openai", "anthropic", "api[_-]?key", "arito\\.vn", "zalo",
                "\\bsk-[a-z0-9_-]{8,}", "begin [a-z ]*private key").map(Pattern::compile).toList();
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String content = Files.readString(file).toLowerCase();
                for (Pattern token : forbidden) {
                    // Comments may mention the gated decision; values may not.
                    content.lines()
                            .filter(line -> !line.stripLeading().startsWith("#") && !line.stripLeading().startsWith("--"))
                            .forEach(line -> assertThat(token.matcher(line).find())
                                    .as(file + " matches " + token).isFalse());
                }
            }
        }
    }
}
