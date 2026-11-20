package com.secureerp.agent.platform.internal;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Cross-cutting technical beans. All time is UTC {@code Instant} (CLAUDE.md). Scheduling is enabled here, for
 * every module, so that switching one job off (e.g. the order sweeper) never stops another (EP-09c reviews).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class PlatformConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
