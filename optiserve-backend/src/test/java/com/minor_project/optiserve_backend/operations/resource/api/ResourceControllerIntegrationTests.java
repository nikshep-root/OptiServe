package com.minor_project.optiserve_backend.operations.resource.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ResourceControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    @Test
    void createsAndReturnsResources() throws Exception {
        mockMvc.perform(post("/api/resources")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateResourceRequest("Counter A"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Counter A"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.compatibleServiceTypes").isEmpty());

        mockMvc.perform(get("/api/resources").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Counter A"));
    }

    @Test
    void getsAndUpdatesAResourceAndItsStatus() throws Exception {
        Resource resource = resourceRepository.saveAndFlush(Resource.create("Counter A", java.util.Set.of()));

        mockMvc.perform(get("/api/resources/{id}", resource.getId()).with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Counter A"));

        mockMvc.perform(patch("/api/resources/{id}", resource.getId())
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Counter B\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Counter B"));

        mockMvc.perform(patch("/api/resources/{id}/status", resource.getId())
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFLINE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFLINE"));

        mockMvc.perform(patch("/api/resources/{id}/status", resource.getId())
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BUSY\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The request conflicts with existing data."));
    }

    @Test
    void managesResourceServiceTypeCompatibility() throws Exception {
        Resource resource = resourceRepository.saveAndFlush(Resource.create("Counter A", java.util.Set.of()));
        ServiceType serviceType = serviceTypeRepository.saveAndFlush(
                ServiceType.create("Registration", null, Duration.ofMinutes(15)));
        serviceType.deactivate();
        serviceTypeRepository.saveAndFlush(serviceType);

        mockMvc.perform(put("/api/resources/{resourceId}/service-types/{serviceTypeId}", resource.getId(), serviceType.getId())
                        .with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compatibleServiceTypes[0].id").value(serviceType.getId().toString()))
                .andExpect(jsonPath("$.compatibleServiceTypes[0].active").value(false));

        mockMvc.perform(get("/api/resources/{resourceId}/service-types", resource.getId()).with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Registration"));

        mockMvc.perform(put("/api/resources/{resourceId}/service-types/{serviceTypeId}", resource.getId(), serviceType.getId())
                        .with(user("operator")))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/resources/{resourceId}/service-types/{serviceTypeId}", resource.getId(), serviceType.getId())
                        .with(user("operator")))
                .andExpect(status().isNoContent());
    }

    @Test
    void returnsValidationAndNotFoundErrors() throws Exception {
        mockMvc.perform(post("/api/resources")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());

        mockMvc.perform(get("/api/resources/{id}", "00000000-0000-0000-0000-000000000000")
                        .with(user("operator")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource was not found."));

        Resource resource = resourceRepository.saveAndFlush(Resource.create("Counter A", java.util.Set.of()));
        mockMvc.perform(put("/api/resources/{resourceId}/service-types/{serviceTypeId}",
                        resource.getId(), "00000000-0000-0000-0000-000000000000")
                        .with(user("operator")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Service type was not found."));
    }

    @Test
    void deletesAnUnreferencedResource() throws Exception {
        Resource resource = resourceRepository.saveAndFlush(Resource.create("Temporary", java.util.Set.of()));

        mockMvc.perform(delete("/api/resources/{id}", resource.getId()).with(user("operator")))
                .andExpect(status().isNoContent());
    }
}
