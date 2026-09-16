package com.minor_project.optiserve_backend.operations.queue.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.Assignment;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.operations.persistence.QueueEntryRepository;
import com.minor_project.optiserve_backend.operations.persistence.AssignmentRepository;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceStageRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import com.minor_project.optiserve_backend.operations.persistence.VehicleRepository;
import com.minor_project.optiserve_backend.operations.servicerequest.api.CreateServiceRequestRequest;
import com.minor_project.optiserve_backend.operations.servicerequest.api.ServiceRequestResponse;
import com.minor_project.optiserve_backend.operations.servicerequest.application.ServiceRequestApplicationService;
import java.time.Duration;
import java.time.Instant;
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
class QueueControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceStageRepository serviceStageRepository;
    @Autowired private QueueEntryRepository queueEntryRepository;
    @Autowired private ResourceRepository resourceRepository;
    @Autowired private AssignmentRepository assignmentRepository;
    @Autowired private ServiceRequestApplicationService serviceRequestApplicationService;

    @Test
    void queuesEligibleStageAndListsOnlyActiveEntries() throws Exception {
        ServiceRequestResponse request = createRequest(PriorityClass.URGENT, 2);
        UUID eligibleStageId = request.stages().getFirst().id();
        UUID pendingStageId = request.stages().get(1).id();

        mockMvc.perform(post("/api/queue/stages/{stageId}", eligibleStageId).with(user("operator")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stageId").value(eligibleStageId.toString()))
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.stageStatus").value("QUEUED"));

        mockMvc.perform(get("/api/queue").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stageId").value(eligibleStageId.toString()));

        mockMvc.perform(post("/api/queue/stages/{stageId}", pendingStageId).with(user("operator")))
                .andExpect(status().isConflict());

        ServiceStage queuedStage = serviceStageRepository.findById(eligibleStageId).orElseThrow();
        assertThat(queuedStage.getStatus()).isEqualTo(ServiceStageStatus.QUEUED);
        assertThat(queueEntryRepository.findByServiceStageIdAndStatus(eligibleStageId, QueueEntryStatus.WAITING))
                .isPresent();
    }

    @Test
    void rejectsDuplicateAndUnknownQueueAttemptsWithoutCreatingPartialEntries() throws Exception {
        ServiceRequestResponse request = createRequest(PriorityClass.NORMAL, 1);
        UUID stageId = request.stages().getFirst().id();
        long entriesBefore = queueEntryRepository.count();

        mockMvc.perform(post("/api/queue/stages/{stageId}", stageId).with(user("operator")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/queue/stages/{stageId}", stageId).with(user("operator")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/queue/stages/{stageId}", UUID.randomUUID()).with(user("operator")))
                .andExpect(status().isNotFound());

        assertThat(queueEntryRepository.count()).isEqualTo(entriesBefore + 1);
    }

    @Test
    void removesWaitingEntryAndMakesStageEligibleAgain() throws Exception {
        ServiceRequestResponse request = createRequest(PriorityClass.NORMAL, 1);
        UUID stageId = request.stages().getFirst().id();
        mockMvc.perform(post("/api/queue/stages/{stageId}", stageId).with(user("operator")))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/queue/stages/{stageId}", stageId).with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REMOVED"))
                .andExpect(jsonPath("$.stageStatus").value("ELIGIBLE"));
        mockMvc.perform(get("/api/queue").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        assertThat(serviceStageRepository.findById(stageId).orElseThrow().getStatus())
                .isEqualTo(ServiceStageStatus.ELIGIBLE);
    }

    @Test
    void returnsZeroWaitAndResponseFieldsWhenACompatibleResourceIsAvailable() throws Exception {
        ServiceRequestResponse request = createRequest(PriorityClass.NORMAL, 1);
        UUID stageId = request.stages().getFirst().id();
        ServiceType serviceType = serviceTypeRepository.findById(request.stages().getFirst().serviceTypeId()).orElseThrow();
        resourceRepository.saveAndFlush(Resource.create("Available " + UUID.randomUUID(), Set.of(serviceType)));
        mockMvc.perform(post("/api/queue/stages/{stageId}", stageId).with(user("operator")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/queue/stages/{stageId}/wait-time", stageId).with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stageId").value(stageId.toString()))
                .andExpect(jsonPath("$.estimatedWaitMinutes").value(0))
                .andExpect(jsonPath("$.queueStatus").value("WAITING"))
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andExpect(jsonPath("$.calculatedAt").exists());
    }

    @Test
    void returnsBusyResourceRemainingWaitUsingTheRealCalculationService() throws Exception {
        ServiceType serviceType = serviceTypeRepository.saveAndFlush(
                ServiceType.create("Repair " + UUID.randomUUID(), null, Duration.ofMinutes(45)));
        ServiceRequestResponse targetRequest = createRequest(PriorityClass.NORMAL, 1, serviceType);
        ServiceRequestResponse activeRequest = createRequest(PriorityClass.NORMAL, 1, serviceType);
        UUID targetStageId = targetRequest.stages().getFirst().id();
        UUID activeStageId = activeRequest.stages().getFirst().id();
        mockMvc.perform(post("/api/queue/stages/{stageId}", targetStageId).with(user("operator")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/queue/stages/{stageId}", activeStageId).with(user("operator")))
                .andExpect(status().isCreated());
        Resource resource = resourceRepository.saveAndFlush(Resource.create("Busy " + UUID.randomUUID(), Set.of(serviceType)));
        ServiceStage activeStage = serviceStageRepository.findById(activeStageId).orElseThrow();
        Instant startedAt = Instant.now().minus(Duration.ofMinutes(14));
        Assignment assignment = Assignment.assign(activeStage, resource, startedAt, Duration.ofMinutes(45));
        assignment.start(startedAt);
        assignmentRepository.saveAndFlush(assignment);

        mockMvc.perform(get("/api/queue/stages/{stageId}/wait-time", targetStageId).with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedWaitMinutes").value(30));

        assertThat(queueEntryRepository.findByServiceStageIdAndStatus(targetStageId, QueueEntryStatus.WAITING)).isPresent();
        assertThat(assignmentRepository.findById(assignment.getId()).orElseThrow().getStatus().name()).isEqualTo("IN_PROGRESS");
        assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getStatus().name()).isEqualTo("BUSY");
    }

    @Test
    void returnsNotFoundForAnUnknownStageWaitTimeRequest() throws Exception {
        mockMvc.perform(get("/api/queue/stages/{stageId}/wait-time", UUID.randomUUID()).with(user("operator")))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsConflictWhenAStageIsNotCurrentlyQueuedAndWaiting() throws Exception {
        ServiceRequestResponse request = createRequest(PriorityClass.NORMAL, 1);
        UUID stageId = request.stages().getFirst().id();

        mockMvc.perform(get("/api/queue/stages/{stageId}/wait-time", stageId).with(user("operator")))
                .andExpect(status().isConflict());
    }

    private ServiceRequestResponse createRequest(PriorityClass priority, int stageCount) {
        ServiceType serviceType = serviceTypeRepository.saveAndFlush(
                ServiceType.create("Inspection " + UUID.randomUUID(), null, Duration.ofMinutes(15)));
        return createRequest(priority, stageCount, serviceType);
    }

    private ServiceRequestResponse createRequest(PriorityClass priority, int stageCount, ServiceType serviceType) {
        Vehicle vehicle = vehicleRepository.saveAndFlush(Vehicle.create(
                UUID.randomUUID(), "KA01AB" + UUID.randomUUID().toString().substring(0, 6), "Toyota", "Camry", 2024));
        return serviceRequestApplicationService.create(new CreateServiceRequestRequest(
                vehicle.getId(), priority, null,
                java.util.Collections.nCopies(stageCount, serviceType.getId())));
    }
}
