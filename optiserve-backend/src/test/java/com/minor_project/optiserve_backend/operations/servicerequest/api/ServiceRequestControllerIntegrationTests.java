package com.minor_project.optiserve_backend.operations.servicerequest.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.authentication.persistence.AuthUserRepository;
import com.minor_project.optiserve_backend.customer.CustomerTestFixtures;
import com.minor_project.optiserve_backend.customer.domain.Customer;
import com.minor_project.optiserve_backend.customer.persistence.CustomerRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceRequestRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceWorkflowRepository;
import com.minor_project.optiserve_backend.operations.persistence.QueueEntryRepository;
import com.minor_project.optiserve_backend.operations.persistence.VehicleRepository;
import com.minor_project.optiserve_backend.operations.servicerequest.application.ServiceRequestApplicationService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
class ServiceRequestControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private AuthUserRepository users;
    @Autowired private CustomerRepository customers;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceRequestRepository serviceRequestRepository;
    @Autowired private ServiceWorkflowRepository serviceWorkflowRepository;
    @Autowired private QueueEntryRepository queueEntryRepository;
    @Autowired private ServiceRequestApplicationService serviceRequestApplicationService;

    @Test
    void createsRequestWorkflowAndOrderedStagesWithoutQueueing() throws Exception {
        Vehicle vehicle = vehicleRepository.saveAndFlush(vehicle());
        ServiceType inspection = serviceTypeRepository.saveAndFlush(serviceType("Inspection"));
        ServiceType repair = serviceTypeRepository.saveAndFlush(serviceType("Repair"));

        mockMvc.perform(post("/api/service-requests")
                        .with(user("operator").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequestRequest(
                                vehicle.getId(), PriorityClass.URGENT, null,
                                List.of(inspection.getId(), repair.getId(), inspection.getId())))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicleId").value(vehicle.getId().toString()))
                .andExpect(jsonPath("$.vehicleRegistrationNumber").value("KA01AB1234"))
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.workflowStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.stages[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$.stages[0].status").value("ELIGIBLE"))
                .andExpect(jsonPath("$.stages[0].predictedServiceDuration").exists())
                .andExpect(jsonPath("$.stages[0].predictionSource").value("DEFAULT_FALLBACK"))
                .andExpect(jsonPath("$.stages[1].sequenceNumber").value(2))
                .andExpect(jsonPath("$.stages[1].status").value("PENDING"))
                .andExpect(jsonPath("$.stages[1].predictedServiceDuration").exists())
                .andExpect(jsonPath("$.stages[1].predictionSource").value("DEFAULT_FALLBACK"))
                .andExpect(jsonPath("$.stages[2].sequenceNumber").value(3))
                .andExpect(jsonPath("$.stages[2].serviceTypeId").value(inspection.getId().toString()))
                .andExpect(jsonPath("$.stages[2].status").value("PENDING"));

        assertThat(serviceRequestRepository.count()).isEqualTo(1);
        assertThat(serviceWorkflowRepository.count()).isEqualTo(1);
        assertThat(queueEntryRepository.count()).isZero();
    }

    @Test
    void returnsRequestByIdAndCollection() throws Exception {
        Vehicle vehicle = vehicleRepository.saveAndFlush(vehicle());
        ServiceType inspection = serviceTypeRepository.saveAndFlush(serviceType("Inspection"));
        ServiceRequestResponse created = serviceRequestApplicationService.create(new CreateServiceRequestRequest(
                vehicle.getId(), PriorityClass.NORMAL, null, List.of(inspection.getId())));

        mockMvc.perform(get("/api/service-requests/{id}", created.id()).with(user("operator").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.id().toString()))
                .andExpect(jsonPath("$.stages[0].serviceTypeName").value("Inspection"));

        mockMvc.perform(get("/api/service-requests").with(user("operator").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(created.id().toString()));
    }

    @Test
    void returnsStandardErrorsAndLeavesNoPartialRequestForInvalidInput() throws Exception {
        ServiceType inspection = serviceTypeRepository.saveAndFlush(serviceType("Inspection"));
        long requestsBefore = serviceRequestRepository.count();
        long workflowsBefore = serviceWorkflowRepository.count();

        mockMvc.perform(post("/api/service-requests")
                        .with(user("operator").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequestRequest(
                                UUID.randomUUID(), PriorityClass.NORMAL, null, List.of(inspection.getId())))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Vehicle was not found."));

        Vehicle vehicle = vehicleRepository.saveAndFlush(vehicle());
        mockMvc.perform(post("/api/service-requests")
                        .with(user("operator").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequestRequest(
                                vehicle.getId(), PriorityClass.NORMAL, null, List.of(UUID.randomUUID())))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Service type was not found."));

        mockMvc.perform(post("/api/service-requests")
                        .with(user("operator").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":\"" + vehicle.getId()
                                + "\",\"priority\":\"NORMAL\",\"serviceTypeIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.serviceTypeIds").exists());

        mockMvc.perform(post("/api/service-requests")
                        .with(user("operator").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequestRequest(
                                vehicle.getId(), PriorityClass.APPOINTMENT, null, List.of(inspection.getId())))))
                .andExpect(status().isBadRequest());

        assertThat(serviceRequestRepository.count()).isEqualTo(requestsBefore);
        assertThat(serviceWorkflowRepository.count()).isEqualTo(workflowsBefore);
    }

    @Test
    void rejectsUnknownRequestId() throws Exception {
        mockMvc.perform(get("/api/service-requests/{id}", UUID.randomUUID()).with(user("operator").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Service request was not found."));
    }

    @Test
    void registeredCustomerCanCreateOwnedVehicleAndRequestButCannotReadAnotherCustomersRequest() throws Exception {
        String email = "flow-" + UUID.randomUUID() + "@example.com";
        String token = register(email);
        mockMvc.perform(put("/api/customers/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Flow Customer\",\"phone\":null}"))
                .andExpect(status().isOk());
        ServiceType inspection = serviceTypeRepository.saveAndFlush(serviceType("Flow Inspection"));
        var vehicleResult = mockMvc.perform(post("/api/vehicles").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationNumber\":\"flow-" + UUID.randomUUID().toString().substring(0, 8)
                                + "\",\"make\":\"Toyota\",\"model\":\"Camry\",\"year\":2024}"))
                .andExpect(status().isCreated()).andReturn();
        String vehicleId = objectMapper.readTree(vehicleResult.getResponse().getContentAsString()).get("id").asString();
        String requestBody = "{\"vehicleId\":\"" + vehicleId
                + "\",\"priority\":\"NORMAL\",\"serviceTypeIds\":[\"" + inspection.getId() + "\"]}";
        var requestResult = mockMvc.perform(post("/api/service-requests").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.vehicleId").value(vehicleId)).andReturn();
        String requestId = objectMapper.readTree(requestResult.getResponse().getContentAsString()).get("id").asString();

        mockMvc.perform(get("/api/service-requests").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(requestId))
                .andExpect(jsonPath("$[1]").doesNotExist());

        String otherToken = register("other-" + UUID.randomUUID() + "@example.com");
        mockMvc.perform(get("/api/service-requests/{id}", requestId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/service-requests").header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isNotFound());
    }

    private String register(String email) throws Exception {
        var result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tokenType").value("Bearer")).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private Vehicle vehicle() {
        Customer customer = CustomerTestFixtures.createPersistedCustomer(users, customers);
        return Vehicle.create(customer, "KA01AB1234", "Toyota", "Camry", 2024);
    }

    private ServiceType serviceType(String name) {
        return ServiceType.create(name, null, Duration.ofMinutes(15));
    }
}
