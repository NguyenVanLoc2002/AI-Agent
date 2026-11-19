package com.secureerp.agent;

import com.secureerp.agent.platform.MutableClock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The web demo (profile {@code demo}, ADR-015) on a random port with a real PostgreSQL, the synthetic
 * demo users of {@code application-demo.yml} and a test clock (EP-04). The demo password is random per
 * run and never written to the repository (decision 2026-11-19).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
@Import({TestcontainersConfiguration.class, DemoWebEnvironment.TestClock.class})
public abstract class DemoWebEnvironment {

    public static final Instant START = Instant.parse("2026-11-19T08:00:00Z");
    protected static final String PASSWORD = "demo-" + UUID.randomUUID();

    @DynamicPropertySource
    static void demoPassword(DynamicPropertyRegistry registry) {
        registry.add("apas.identity.demo-password", () -> PASSWORD);
        registry.add("server.address", () -> "127.0.0.1"); // DemoProfileGuard: loopback only
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClock {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetClock() {
        clock.set(START);
    }

    protected DemoHttpClient client() {
        return new DemoHttpClient(port);
    }

    /** A client logged in as one of the synthetic demo users; fails the test if the login fails. */
    protected DemoHttpClient loggedIn(String username) {
        DemoHttpClient client = client();
        int status = client.login(username, PASSWORD).statusCode();
        if (status != 200) {
            throw new AssertionError("login of " + username + " failed with " + status);
        }
        return client;
    }
}
