package com.minor_project.optiserve_backend.operations.servicerequest.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
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
                        .with(user("operator"))
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
                .andExpect(jsonPath("$.stages[1].sequenceNumber").value(2))
                .andExpect(jsonPath("$.stages[1].status").value("PENDING"))
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

        mockMvc.perform(get("/api/service-requests/{id}", created.id()).with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.id().toString()))
                .andExpect(jsonPath("$.stages[0].serviceTypeName").value("Inspection"));

        mockMvc.perform(get("/api/service-requests").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(created.id().toString()));
    }

    @Test
    void returnsStandardErrorsAndLeavesNoPartialRequestForInvalidInput() throws Exception {
        ServiceType inspection = serviceTypeRepository.saveAndFlush(serviceType("Inspection"));
        long requestsBefore = serviceRequestRepository.count();
        long workflowsBefore = serviceWorkflowRepository.count();

        mockMvc.perform(post("/api/service-requests")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequestRequest(
                                UUID.randomUUID(), PriorityClass.NORMAL, null, List.of(inspection.getId())))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Vehicle was not found."));

        Vehicle vehicle = vehicleRepository.saveAndFlush(vehicle());
        mockMvc.perform(post("/api/service-requests")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequestRequest(
                                vehicle.getId(), PriorityClass.NORMAL, null, List.of(UUID.randomUUID())))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Service type was not found."));

        mockMvc.perform(post("/api/service-requests")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":\"" + vehicle.getId()
                                + "\",\"priority\":\"NORMAL\",\"serviceTypeIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.serviceTypeIds").exists());

        mockMvc.perform(post("/api/service-requests")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequestRequest(
                                vehicle.getId(), PriorityClass.APPOINTMENT, null, List.of(inspection.getId())))))
                .andExpect(status().isBadRequest());

        assertThat(serviceRequestRepository.count()).isEqualTo(requestsBefore);
        assertThat(serviceWorkflowRepository.count()).isEqualTo(workflowsBefore);
    }

    @Test
    void rejectsUnknownRequestId() throws Exception {
        mockMvc.perform(get("/api/service-requests/{id}", UUID.randomUUID()).with(user("operator")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Service request was not found."));
    }

    private Vehicle vehicle() {
        return Vehicle.create(UUID.randomUUID(), "KA01AB1234", "Toyota", "Camry", 2024);
    }

    private ServiceType serviceType(String name) {
        return ServiceType.create(name, null, Duration.ofMinutes(15));
    }
}
