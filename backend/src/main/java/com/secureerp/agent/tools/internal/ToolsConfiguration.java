package com.secureerp.agent.tools.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@code apas.tools.limits} (D-10 defaults). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ToolLimits.class)
class ToolsConfiguration {
}
