package com.minor_project.optiserve_backend.configuration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Test
    void publicProbeEndpointsExposeOnlyStatusAndReadinessIncludesPostgres() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("jdbc"))));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(environment.getProperty(
                        "management.endpoint.health.group.readiness.include"))
                .isEqualTo("readinessState,db");
        org.assertj.core.api.Assertions.assertThat(environment.getProperty(
                        "management.endpoint.health.group.liveness.include"))
                .isEqualTo("livenessState");
    }

    @Test
    void onlyAdminCanReadMetricsAndUnexposedManagementEndpointsRemainUnavailable() throws Exception {
        mockMvc.perform(get("/actuator/metrics/optiserve.ml.predictions.success"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/metrics/optiserve.ml.predictions.success").with(user("customer")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/metrics/optiserve.ml.predictions.success")
                        .with(user("operator").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("optiserve.ml.predictions.success"))
                .andExpect(jsonPath("$.measurements[0].value").isNumber());

        mockMvc.perform(get("/actuator/env").with(user("operator").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthComponentPathsAreNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/db"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/health/db").with(user("customer")))
                .andExpect(status().isForbidden());
    }
}
