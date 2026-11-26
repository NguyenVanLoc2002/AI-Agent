package com.secureerp.agent.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.DemoWebEnvironment;
import java.net.http.HttpResponse;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;

/**
 * FE-DEPLOY (A-74, 4A; review QA 5): the wiring of the {@code tls} profile without a browser. The profile turns on
 * {@code apas.web.upgrade-insecure-requests}, and with it every answer's CSP ends with {@code upgrade-insecure-requests}.
 * (HSTS needs a real HTTPS request: frontend/e2e/tls.spec.ts.)
 */
@TestPropertySource(properties = "apas.web.upgrade-insecure-requests=true")
class FrontendServingUpgradeIT extends DemoWebEnvironment {

    @Test
    void theTlsProfileTurnsTheUpgradeOn() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-tls.yml"));
        Properties tls = yaml.getObject();
        assertThat(tls).isNotNull();
        assertThat(tls.getProperty("apas.web.upgrade-insecure-requests")).isEqualTo("true");
        assertThat(tls.getProperty("server.ssl.enabled")).isEqualTo("true");
        // No keystore or password in the repository: both come from the environment.
        assertThat(tls.getProperty("server.ssl.key-store")).isEqualTo("${APAS_TLS_KEYSTORE}");
        assertThat(tls.getProperty("server.ssl.key-store-password")).isEqualTo("${APAS_TLS_KEYSTORE_PASSWORD}");
    }

    @Test
    void withTheUpgradeOnEveryAnswerAsksTheBrowserToUpgrade() {
        String expected = FrontendServingIT.CSP + "; upgrade-insecure-requests";
        for (String path : new String[] {"/", "/api/auth/csrf", "/api/ops/kill-switch", "/a;b"}) {
            HttpResponse<String> response = client().get(path);
            assertThat(response.headers().firstValue("content-security-policy")).as(path).contains(expected);
        }
    }
}
