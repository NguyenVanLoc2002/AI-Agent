package com.secureerp.agent.platform;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type that may be serialized towards a model provider (ADR-003, ARCH-06).
 *
 * <p>Every record and interface in {@code gateway.api} (and later {@code tools.api.dto}) must carry
 * {@code @EgressClassified(APPROVED_TECHNICAL)}; {@code ArchitectureRulesTest} fails the build
 * otherwise. Lives in {@code platform} so every module that owns egress DTOs can use it without a
 * new module dependency.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EgressClassified {

    DataClassification value();
}
