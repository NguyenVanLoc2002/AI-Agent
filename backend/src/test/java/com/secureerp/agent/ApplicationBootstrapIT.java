package com.secureerp.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Full application context against a real PostgreSQL (Testcontainers): Flyway baseline applied,
 * health endpoint UP, other actuator endpoints not exposed, no business tables created.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApplicationBootstrapIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayBaselineApplied() {
        List<String> applied = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertThat(applied).containsExactly("1");
    }

    @Test
    void noBusinessTablesBeforeSchemaApproval() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);
        assertThat(tables).containsExactly("flyway_schema_history");
    }

    @Test
    void healthIsUpWithoutDetails() throws Exception {
        MvcResult result = mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("postgres").doesNotContain("jdbc");
    }

    @Test
    void livenessAndReadinessProbesAreUp() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        for (String path : List.of("/actuator/env", "/actuator/beans", "/actuator/configprops",
                "/actuator/heapdump", "/actuator/loggers", "/actuator/info", "/actuator/mappings")) {
            mvc.perform(get(path)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Test
    void noDemoEndpointsExistYet() throws Exception {
        mvc.perform(get("/api/demo/conversations")).andExpect(status().isNotFound());
    }
}
