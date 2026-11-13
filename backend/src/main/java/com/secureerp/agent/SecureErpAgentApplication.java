package com.secureerp.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstrap entry point of the modular monolith.
 *
 * <p>The {@code bootstrap} role from docs/architecture/MODULE_BOUNDARIES.md is played by this root
 * package: Spring Modulith treats every direct sub-package of the application class as an
 * application module, so the application class itself must live here (DESIGN PROPOSAL, ADR-001).
 */
@SpringBootApplication
public class SecureErpAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureErpAgentApplication.class, args);
    }
}
