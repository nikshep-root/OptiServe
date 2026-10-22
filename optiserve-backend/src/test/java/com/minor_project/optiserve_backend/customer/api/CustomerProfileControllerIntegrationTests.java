package com.minor_project.optiserve_backend.customer.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minor_project.optiserve_backend.authentication.persistence.AuthUserRepository;
import com.minor_project.optiserve_backend.customer.CustomerTestFixtures;
import com.minor_project.optiserve_backend.customer.persistence.CustomerRepository;
import com.minor_project.optiserve_backend.operations.persistence.VehicleRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerProfileControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthUserRepository users;
    @Autowired private CustomerRepository customers;
    @Autowired private VehicleRepository vehicles;

    @Test
    void customerCanCreateReadUpdateAndDeleteTheirOwnProfile() throws Exception {
        var user = users.saveAndFlush(com.minor_project.optiserve_backend.authentication.domain.AuthUser
                .create("profile-" + UUID.randomUUID() + "@example.com", "test-hash"));

        mockMvc.perform(get("/api/customers/me").with(user(user.getUsername())))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/customers/me").with(user(user.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" First Name \",\"phone\":\" 123 \\u2600\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("First Name"));
        mockMvc.perform(get("/api/customers/me").with(user(user.getUsername())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("123 ☀"))
                .andExpect(jsonPath("$.user").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist());
        mockMvc.perform(put("/api/customers/me").with(user(user.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Updated Name\",\"phone\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated Name"));
        mockMvc.perform(delete("/api/customers/me").with(user(user.getUsername())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/customers/me").with(user(user.getUsername())))
                .andExpect(status().isNotFound());
    }

    @Test
    void validatesProfileAndPreventsDeletionWhileVehiclesExist() throws Exception {
        var customer = CustomerTestFixtures.createPersistedCustomer(users, customers);
        mockMvc.perform(put("/api/customers/me").with(user(customer.getUser().getUsername()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \",\"phone\":null}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists());
        vehicles.saveAndFlush(com.minor_project.optiserve_backend.operations.domain.Vehicle.create(
                customer, "KA01AB1234", "Toyota", "Camry", 2024));
        mockMvc.perform(delete("/api/customers/me").with(user(customer.getUser().getUsername())))
                .andExpect(status().isConflict());
    }

    @Test
    void profileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/customers/me")).andExpect(status().isUnauthorized());
    }
}
