package com.minor_project.optiserve_backend.operations.scheduler.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SchedulingDecisionControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;

    @Test
    void decisionHistoryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/scheduling/decisions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void decisionHistoryIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/admin/scheduling/decisions")
                        .with(user("customer@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadAnEmptyBoundedDecisionPageAndMissingDetailsAreNotFound() throws Exception {
        mockMvc.perform(get("/api/admin/scheduling/decisions")
                        .with(user("operator@example.com").roles("ADMIN"))
                        .param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(100));

        mockMvc.perform(get("/api/admin/scheduling/decisions/{id}", UUID.randomUUID())
                        .with(user("operator@example.com").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidDateRangeIsRejectedAsBadRequest() throws Exception {
        mockMvc.perform(get("/api/admin/scheduling/decisions")
                        .with(user("operator@example.com").roles("ADMIN"))
                        .param("from", "2026-10-02T00:00:00Z")
                        .param("to", "2026-10-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidDecisionFilterIsRejectedAsBadRequest() throws Exception {
        mockMvc.perform(get("/api/admin/scheduling/decisions")
                        .with(user("operator@example.com").roles("ADMIN"))
                        .param("trigger", "NOT_A_TRIGGER"))
                .andExpect(status().isBadRequest());
    }
}
