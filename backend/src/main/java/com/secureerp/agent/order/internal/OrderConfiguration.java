package com.secureerp.agent.order.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ErpCapabilities.class, OrderProperties.class})
class OrderConfiguration {
}
