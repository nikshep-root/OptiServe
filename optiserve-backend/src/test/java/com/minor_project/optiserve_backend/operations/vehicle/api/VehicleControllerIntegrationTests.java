package com.minor_project.optiserve_backend.operations.vehicle.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minor_project.optiserve_backend.authentication.persistence.AuthUserRepository;
import com.minor_project.optiserve_backend.customer.CustomerTestFixtures;
import com.minor_project.optiserve_backend.customer.domain.Customer;
import com.minor_project.optiserve_backend.customer.persistence.CustomerRepository;
import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import com.minor_project.optiserve_backend.operations.persistence.VehicleRepository;
import com.minor_project.optiserve_backend.operations.servicerequest.api.CreateServiceRequestRequest;
import com.minor_project.optiserve_backend.operations.servicerequest.application.ServiceRequestApplicationService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VehicleControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private AuthUserRepository users;
    @Autowired private CustomerRepository customers;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceRequestApplicationService serviceRequestService;

    @Test
    void customerCanCreateListReadAndUpdateOnlyTheirVehicles() throws Exception {
        Customer owner = customer();
        Customer other = customer();
        Vehicle otherVehicle = vehicleRepository.saveAndFlush(
                Vehicle.create(other, "OTHER-123", "Honda", "City", 2023));
        String identity = owner.getUser().getUsername();
        String request = vehicleJson("ka01ab1234", "Toyota", "Camry", 2024);

        var create = mockMvc.perform(post("/api/vehicles")
                        .with(user(identity)).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationNumber").value("KA01AB1234"))
                .andReturn();
        String vehicleId = objectMapper.readTree(create.getResponse().getContentAsString()).get("id").asString();

        mockMvc.perform(get("/api/vehicles").with(user(identity)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(vehicleId))
                .andExpect(jsonPath("$[1]").doesNotExist());
        mockMvc.perform(get("/api/vehicles/{id}", vehicleId).with(user(identity)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.make").value("Toyota"));
        mockMvc.perform(put("/api/vehicles/{id}", vehicleId).with(user(identity))
                        .contentType(MediaType.APPLICATION_JSON).content(vehicleJson("ka01xy9999", "Toyota", "Corolla", 2022)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.registrationNumber").value("KA01XY9999"));

        mockMvc.perform(get("/api/vehicles/{id}", otherVehicle.getId()).with(user(identity)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/vehicles/{id}", otherVehicle.getId()).with(user(identity))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/vehicles/{id}", otherVehicle.getId()).with(user(identity)))
                .andExpect(status().isNotFound());
    }

    @Test
    void validatesVehicleInputAndProtectsServiceHistory() throws Exception {
        Customer owner = customer();
        String identity = owner.getUser().getUsername();
        mockMvc.perform(post("/api/vehicles").with(user(identity)).contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson(" ", "Toyota", "Camry", 2024)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.registrationNumber").exists());

        Vehicle vehicle = vehicleRepository.saveAndFlush(Vehicle.create(owner, "KA01AB1234", "Toyota", "Camry", 2024));
        ServiceType serviceType = serviceTypeRepository.saveAndFlush(
                ServiceType.create("Inspection " + UUID.randomUUID(), null, Duration.ofMinutes(20)));
        serviceRequestService.create(new CreateServiceRequestRequest(
                vehicle.getId(), PriorityClass.NORMAL, null, List.of(serviceType.getId())));

        mockMvc.perform(delete("/api/vehicles/{id}", vehicle.getId()).with(user(identity)))
                .andExpect(status().isConflict());
        assertThat(vehicleRepository.findById(vehicle.getId())).isPresent();
    }

    @Test
    void vehicleCollectionRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/vehicles")).andExpect(status().isUnauthorized());
    }

    private Customer customer() {
        return CustomerTestFixtures.createPersistedCustomer(users, customers);
    }

    private static String vehicleJson(String registration, String make, String model, int year) {
        return "{\"registrationNumber\":\"" + registration + "\",\"make\":\"" + make
                + "\",\"model\":\"" + model + "\",\"year\":" + year + "}";
    }
}
