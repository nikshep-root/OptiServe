package com.minor_project.optiserve_backend.operations.assignment.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.minor_project.optiserve_backend.operations.assignment.api.AssignmentNextResponse;
import com.minor_project.optiserve_backend.operations.assignment.api.AssignmentNextResult;
import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.operations.persistence.AssignmentRepository;
import com.minor_project.optiserve_backend.operations.persistence.QueueEntryRepository;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceRequestRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceStageRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceWorkflowRepository;
import com.minor_project.optiserve_backend.operations.persistence.VehicleRepository;
import com.minor_project.optiserve_backend.operations.queue.application.QueueApplicationService;
import com.minor_project.optiserve_backend.operations.servicerequest.api.CreateServiceRequestRequest;
import com.minor_project.optiserve_backend.operations.servicerequest.api.ServiceRequestResponse;
import com.minor_project.optiserve_backend.operations.servicerequest.application.ServiceRequestApplicationService;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AssignmentConcurrencyIntegrationTests {

    @Autowired private AssignmentApplicationService assignmentService;
    @Autowired private ServiceRequestApplicationService serviceRequests;
    @Autowired private QueueApplicationService queue;
    @Autowired private ServiceTypeRepository serviceTypes;
    @Autowired private VehicleRepository vehicles;
    @Autowired private ResourceRepository resources;
    @Autowired private AssignmentRepository assignments;
    @Autowired private QueueEntryRepository queueEntries;
    @Autowired private ServiceStageRepository stages;
    @Autowired private ServiceWorkflowRepository workflows;
    @Autowired private ServiceRequestRepository requests;

    private UUID resourceId;
    private UUID serviceTypeId;
    private final List<UUID> vehicleIds = new java.util.ArrayList<>();
    private final List<UUID> requestIds = new java.util.ArrayList<>();
    private final List<UUID> workflowIds = new java.util.ArrayList<>();
    private final List<UUID> stageIds = new java.util.ArrayList<>();

    @Test
    void parallelAssignmentOperationsUseSeparateTransactionsAndClaimResourceOnlyOnce() throws Exception {
        ServiceType type = serviceTypes.saveAndFlush(ServiceType.create("Inspection " + UUID.randomUUID(), null, Duration.ofMinutes(15)));
        serviceTypeId = type.getId();
        Resource resource = resources.saveAndFlush(Resource.create("Only Bay " + UUID.randomUUID(), Set.of(type)));
        resourceId = resource.getId();
        queuedRequest(type);
        queuedRequest(type);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AssignmentNextResponse> first = executor.submit(() -> { barrier.await(); return assignmentService.assignNext(); });
            Future<AssignmentNextResponse> second = executor.submit(() -> { barrier.await(); return assignmentService.assignNext(); });
            List<AssignmentNextResponse> results = List.of(first.get(), second.get());

            assertThat(results).extracting(AssignmentNextResponse::result)
                    .contains(AssignmentNextResult.ASSIGNED);
            assertThat(results.stream().filter(result -> result.result() == AssignmentNextResult.ASSIGNED)).hasSize(1);
            assertThat(assignments.findAll()).hasSize(1);
            assertThat(assignments.findAll().getFirst().getResource().getId()).isEqualTo(resourceId);
            assertThat(resources.findById(resourceId).orElseThrow().getStatus().name()).isEqualTo("BUSY");
        } finally {
            executor.shutdownNow();
        }
    }

    @AfterEach
    void cleanup() {
        if (resourceId == null) return;
        assignments.deleteAll(assignments.findAll().stream()
                .filter(assignment -> resourceId.equals(assignment.getResource().getId())).toList());
        queueEntries.deleteAll(queueEntries.findAll().stream()
                .filter(entry -> stageIds.contains(entry.getServiceStage().getId())).toList());
        stages.deleteAllById(stageIds);
        workflows.deleteAllById(workflowIds);
        requests.deleteAllById(requestIds);
        vehicles.deleteAllById(vehicleIds);
        resources.deleteById(resourceId);
        serviceTypes.deleteById(serviceTypeId);
    }

    private void queuedRequest(ServiceType type) {
        Vehicle vehicle = vehicles.saveAndFlush(Vehicle.create(UUID.randomUUID(), "KA" + UUID.randomUUID().toString().substring(0, 8), "Toyota", "Camry", 2024));
        vehicleIds.add(vehicle.getId());
        ServiceRequestResponse request = serviceRequests.create(new CreateServiceRequestRequest(vehicle.getId(), PriorityClass.NORMAL, null, List.of(type.getId())));
        requestIds.add(request.id());
        workflowIds.add(request.workflowId());
        stageIds.add(request.stages().getFirst().id());
        queue.queueStage(request.stages().getFirst().id());
    }
}
