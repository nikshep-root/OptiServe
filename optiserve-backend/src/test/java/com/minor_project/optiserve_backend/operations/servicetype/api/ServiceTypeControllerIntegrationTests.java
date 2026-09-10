package com.minor_project.optiserve_backend.operations.servicetype.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ServiceTypeControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    @Test
    void createsAServiceType() throws Exception {
        mockMvc.perform(post("/api/service-types")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateServiceTypeRequest("Registration", "Front desk", 900L, true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Registration"))
                .andExpect(jsonPath("$.defaultServiceDurationSeconds").value(900))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void returnsServiceTypeCollectionAndItemById() throws Exception {
        ServiceType persisted = serviceTypeRepository.saveAndFlush(
                ServiceType.create("Consultation", null, Duration.ofMinutes(20)));

        mockMvc.perform(get("/api/service-types").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Consultation"));

        mockMvc.perform(get("/api/service-types/{id}", persisted.getId()).with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(persisted.getId().toString()))
                .andExpect(jsonPath("$.name").value("Consultation"));
    }

    @Test
    void patchesAServiceType() throws Exception {
        ServiceType persisted = serviceTypeRepository.saveAndFlush(
                ServiceType.create("Consultation", "Initial", Duration.ofMinutes(20)));

        mockMvc.perform(patch("/api/service-types/{id}", persisted.getId())
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Extended consultation\",\"defaultServiceDurationSeconds\":1800,\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Extended consultation"))
                .andExpect(jsonPath("$.defaultServiceDurationSeconds").value(1800))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void returnsStandardValidationAndNotFoundErrors() throws Exception {
        mockMvc.perform(post("/api/service-types")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"defaultServiceDurationSeconds\":0,\"active\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.defaultServiceDurationSeconds").exists());

        mockMvc.perform(post("/api/service-types")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Registration\",\"defaultServiceDurationSeconds\":\"invalid\",\"active\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is invalid."));

        mockMvc.perform(get("/api/service-types/{id}", "00000000-0000-0000-0000-000000000000")
                        .with(user("operator")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Service type was not found."));
    }

    @Test
    void returnsConflictForCaseInsensitiveDuplicateName() throws Exception {
        serviceTypeRepository.saveAndFlush(ServiceType.create("Registration", null, Duration.ofMinutes(15)));

        mockMvc.perform(post("/api/service-types")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateServiceTypeRequest("registration", null, 900L, true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The request conflicts with existing data."));
    }

    @Test
    void deletesAnUnreferencedServiceType() throws Exception {
        ServiceType persisted = serviceTypeRepository.saveAndFlush(
                ServiceType.create("Temporary", null, Duration.ofMinutes(15)));

        mockMvc.perform(delete("/api/service-types/{id}", persisted.getId()).with(user("operator")))
                .andExpect(status().isNoContent());
    }
}
