package com.secureerp.agent.order.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** EP-09c: the sweep interval is bounded (a tiny one loads the database, one past the timeout is late). */
class OrderPropertiesTest {

    @Test
    void theSweepIntervalIsBounded() {
        Duration timeout = Duration.ofMinutes(2);
        assertThat(new OrderProperties(timeout, new OrderProperties.Sweeper(Duration.ofSeconds(30)))
                .sweeper().interval()).isEqualTo(Duration.ofSeconds(30));
        for (Duration interval : new Duration[] {Duration.ofMillis(99), Duration.ofMinutes(3), Duration.ZERO}) {
            assertThatThrownBy(() -> new OrderProperties(timeout, new OrderProperties.Sweeper(interval)))
                    .as(interval.toString()).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new OrderProperties(timeout, null)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Round-2 review L6: the shipped configuration keeps the sweeper on, every 30 s (tests switch it off). */
    @Test
    void theShippedConfigurationSweepsEveryThirtySeconds() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        PropertySource<?> main = sources.getFirst();

        assertThat(String.valueOf(main.getProperty("apas.order.sweeper.enabled"))).isEqualTo("true");
        assertThat(String.valueOf(main.getProperty("apas.order.sweeper.interval"))).isEqualTo("PT30S");
    }
}
