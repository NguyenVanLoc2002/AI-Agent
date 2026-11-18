package com.secureerp.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.secureerp.agent.gateway.AiGateway;
import com.secureerp.agent.gateway.EgressFixtures;
import com.secureerp.agent.gateway.GatewayResult;
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
 * health endpoint UP, other actuator endpoints not exposed, only approved tables created, and the
 * gateway fails closed while no Fake provider is configured.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApplicationBootstrapIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AiGateway gateway;

    @Test
    void flywayBaselineApplied() {
        List<String> applied = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertThat(applied).containsExactly("1", "2", "3", "4", "5", "6");
    }

    @Test
    void onlyApprovedTablesExist() {
        // V2 (SEC-01a, ADR-012 ACCEPTED): egress_record is the only application table so far.
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);
        assertThat(tables).containsExactlyInAnyOrder("flyway_schema_history", "egress_record", "tool_invocation",
                "product_ref", "conversation_vin", // V5 (EP-05): current VIN per conversation, module catalog
                "quote", "quote_line", "quote_handle", "quote_request"); // V6 (EP-06): module quote
    }

    @Test
    void gatewayWithoutConfiguredProviderFailsClosedButAudits() {
        GatewayResult result = gateway.complete(EgressFixtures.request("xin chao"));
        assertThat(result).isEqualTo(new GatewayResult.Failed(GatewayResult.FailureReason.PROVIDER_UNAVAILABLE));
        assertThat(jdbc.queryForObject("select count(*) from egress_record where trace_id = ?", Integer.class,
                EgressFixtures.TRACE_ID)).isPositive();
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
