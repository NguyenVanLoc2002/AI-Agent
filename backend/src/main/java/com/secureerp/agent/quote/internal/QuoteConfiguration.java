package com.secureerp.agent.quote.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(QuoteProperties.class)
class QuoteConfiguration {
}
