package com.minor_project.optiserve_backend.operations.assignment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.operations.persistence.AssignmentRepository;
import com.minor_project.optiserve_backend.operations.persistence.QueueEntryRepository;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceStageRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import com.minor_project.optiserve_backend.operations.persistence.VehicleRepository;
import com.minor_project.optiserve_backend.operations.queue.application.QueueApplicationService;
import com.minor_project.optiserve_backend.operations.servicerequest.api.CreateServiceRequestRequest;
import com.minor_project.optiserve_backend.operations.servicerequest.api.ServiceRequestResponse;
import com.minor_project.optiserve_backend.operations.servicerequest.application.ServiceRequestApplicationService;
import java.time.Duration;
import java.util.List;
import java.util.Set;
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
class AssignmentControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private VehicleRepository vehicles;
    @Autowired private ServiceTypeRepository serviceTypes;
    @Autowired private ResourceRepository resources;
    @Autowired private ServiceStageRepository stages;
    @Autowired private QueueEntryRepository queueEntries;
    @Autowired private AssignmentRepository assignments;
    @Autowired private ServiceRequestApplicationService serviceRequests;
    @Autowired private QueueApplicationService queue;

    @Test
    void assignsNextStageAndReturnsAssignmentDetails() throws Exception {
        ServiceRequestResponse request = queuedRequest();
        Resource resource = resources.saveAndFlush(Resource.create("Inspection Bay", Set.of(serviceType())));

        mockMvc.perform(post("/api/assignments/next").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignmentId").exists())
                .andExpect(jsonPath("$.stageId").value(request.stages().getFirst().id().toString()))
                .andExpect(jsonPath("$.resourceId").value(resource.getId().toString()))
                .andExpect(jsonPath("$.resourceName").value("Inspection Bay"))
                .andExpect(jsonPath("$.assignmentStatus").value("ASSIGNED"));

        assertThat(stages.findById(request.stages().getFirst().id()).orElseThrow().getStatus())
                .isEqualTo(ServiceStageStatus.ASSIGNED);
        assertThat(resources.findById(resource.getId()).orElseThrow().getStatus()).isEqualTo(ResourceStatus.BUSY);
        assertThat(queueEntries.findByServiceStageIdAndStatus(request.stages().getFirst().id(), QueueEntryStatus.WAITING))
                .isEmpty();
        assertThat(assignments.count()).isEqualTo(1);
    }

    @Test
    void returnsCleanEmptyResultsForNoQueueAndNoCompatibleResource() throws Exception {
        mockMvc.perform(post("/api/assignments/next").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NO_QUEUED_STAGE"))
                .andExpect(jsonPath("$.assignmentId").doesNotExist());

        ServiceRequestResponse request = queuedRequest();
        mockMvc.perform(post("/api/assignments/next").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NO_COMPATIBLE_RESOURCE"));
        assertThat(stages.findById(request.stages().getFirst().id()).orElseThrow().getStatus())
                .isEqualTo(ServiceStageStatus.QUEUED);
        assertThat(queueEntries.findByServiceStageIdAndStatus(request.stages().getFirst().id(), QueueEntryStatus.WAITING))
                .isPresent();
    }

    private ServiceRequestResponse queuedRequest() {
        ServiceType type = serviceTypes.saveAndFlush(ServiceType.create("Inspection " + UUID.randomUUID(), null, Duration.ofMinutes(15)));
        Vehicle vehicle = vehicles.saveAndFlush(Vehicle.create(UUID.randomUUID(), "KA" + UUID.randomUUID().toString().substring(0, 8), "Toyota", "Camry", 2024));
        ServiceRequestResponse request = serviceRequests.create(new CreateServiceRequestRequest(
                vehicle.getId(), PriorityClass.NORMAL, null, List.of(type.getId())));
        queue.queueStage(request.stages().getFirst().id());
        return request;
    }

    private ServiceType serviceType() {
        return serviceTypes.findAll().getFirst();
    }
}
