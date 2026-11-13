package com.secureerp.agent.platform.internal;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Cross-cutting technical beans. All time is UTC {@code Instant} (CLAUDE.md). */
@Configuration(proxyBeanMethods = false)
class PlatformConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
