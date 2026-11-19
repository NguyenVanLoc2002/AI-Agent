package com.secureerp.agent.conversation.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConversationProperties.class)
class ConversationConfiguration {
}
