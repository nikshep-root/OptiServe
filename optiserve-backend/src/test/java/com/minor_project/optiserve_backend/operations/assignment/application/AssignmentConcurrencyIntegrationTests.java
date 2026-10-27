package com.minor_project.optiserve_backend.operations.assignment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.minor_project.optiserve_backend.common.api.StateConflictException;
import com.minor_project.optiserve_backend.operations.assignment.api.AssignmentNextResponse;
import com.minor_project.optiserve_backend.operations.assignment.api.AssignmentNextResult;
import com.minor_project.optiserve_backend.operations.domain.AssignmentStatus;
import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.authentication.persistence.AuthUserRepository;
import com.minor_project.optiserve_backend.customer.CustomerTestFixtures;
import com.minor_project.optiserve_backend.customer.domain.Customer;
import com.minor_project.optiserve_backend.customer.persistence.CustomerRepository;
import com.minor_project.optiserve_backend.operations.persistence.AssignmentRepository;
import com.minor_project.optiserve_backend.operations.persistence.QueueEntryRepository;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceRequestRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceStageRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceWorkflowRepository;
import com.minor_project.optiserve_backend.operations.persistence.VehicleRepository;
import com.minor_project.optiserve_backend.operations.resource.api.UpdateResourceStatusRequest;
import com.minor_project.optiserve_backend.operations.resource.application.ResourceApplicationService;
import com.minor_project.optiserve_backend.operations.queue.application.QueueApplicationService;
import com.minor_project.optiserve_backend.operations.servicerequest.api.CreateServiceRequestRequest;
import com.minor_project.optiserve_backend.operations.servicerequest.api.ServiceRequestResponse;
import com.minor_project.optiserve_backend.operations.servicerequest.application.ServiceRequestApplicationService;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AssignmentConcurrencyIntegrationTests {

    @Autowired private AssignmentApplicationService assignmentService;
    @Autowired private ResourceApplicationService resourceService;
    @Autowired private MockMvc mockMvc;
    @Autowired private ServiceRequestApplicationService serviceRequests;
    @Autowired private QueueApplicationService queue;
    @Autowired private ServiceTypeRepository serviceTypes;
    @Autowired private VehicleRepository vehicles;
    @Autowired private AuthUserRepository users;
    @Autowired private CustomerRepository customers;
    @Autowired private ResourceRepository resources;
    @Autowired private AssignmentRepository assignments;
    @Autowired private QueueEntryRepository queueEntries;
    @Autowired private ServiceStageRepository stages;
    @Autowired private ServiceWorkflowRepository workflows;
    @Autowired private ServiceRequestRepository requests;

    private UUID resourceId;
    private UUID serviceTypeId;
    private final List<UUID> vehicleIds = new java.util.ArrayList<>();
    private final List<UUID> customerIds = new java.util.ArrayList<>();
    private final List<UUID> userIds = new java.util.ArrayList<>();
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

    @Test
    void concurrentAssignmentAndAvailableStatusChangeCannotLeaveActiveWorkOnAvailableResource() throws Exception {
        ServiceType type = serviceTypes.saveAndFlush(
                ServiceType.create("Status race " + UUID.randomUUID(), null, Duration.ofMinutes(15)));
        serviceTypeId = type.getId();
        Resource resource = resources.saveAndFlush(Resource.create("Status race bay " + UUID.randomUUID(), Set.of(type)));
        resourceId = resource.getId();
        queuedRequest(type);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AssignmentNextResponse> assignment = executor.submit(() -> {
                barrier.await();
                return assignmentService.assignNext();
            });
            Future<Boolean> statusChange = executor.submit(() -> {
                barrier.await();
                try {
                    resourceService.changeStatus(resourceId, new UpdateResourceStatusRequest(ResourceStatus.AVAILABLE));
                    return true;
                } catch (StateConflictException expectedIfAssignmentWins) {
                    return false;
                }
            });

            assertThat(assignment.get().result()).isEqualTo(AssignmentNextResult.ASSIGNED);
            statusChange.get();
            assertThat(assignments.existsByResourceIdAndStatusIn(
                    resourceId, List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.IN_PROGRESS))).isTrue();
            assertThat(resources.findById(resourceId).orElseThrow().getStatus()).isEqualTo(ResourceStatus.BUSY);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentCompletionAndOfflineChangeLeaveCompletedResourceOffline() throws Exception {
        ServiceType type = serviceTypes.saveAndFlush(
                ServiceType.create("Completion status race " + UUID.randomUUID(), null, Duration.ofMinutes(15)));
        serviceTypeId = type.getId();
        Resource resource = resources.saveAndFlush(Resource.create("Completion race bay " + UUID.randomUUID(), Set.of(type)));
        resourceId = resource.getId();
        queuedRequest(type);
        UUID assignmentId = assignmentService.assignNext().assignmentId();
        assignmentService.start(assignmentId);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> completion = executor.submit(() -> {
                barrier.await();
                assignmentService.complete(assignmentId, 12L);
                return null;
            });
            Future<?> offlineChange = executor.submit(() -> {
                barrier.await();
                resourceService.changeStatus(resourceId, new UpdateResourceStatusRequest(ResourceStatus.OFFLINE));
                return null;
            });

            completion.get();
            offlineChange.get();
            assertThat(assignments.findById(assignmentId).orElseThrow().getStatus())
                    .isEqualTo(AssignmentStatus.COMPLETED);
            assertThat(resources.findById(resourceId).orElseThrow().getStatus()).isEqualTo(ResourceStatus.OFFLINE);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentCompletionAndAdminAvailableRequestLeaveCompletedResourceAvailable() throws Exception {
        ServiceType type = serviceTypes.saveAndFlush(
                ServiceType.create("Completion available race " + UUID.randomUUID(), null, Duration.ofMinutes(15)));
        serviceTypeId = type.getId();
        Resource resource = resources.saveAndFlush(
                Resource.create("Completion available bay " + UUID.randomUUID(), Set.of(type)));
        resourceId = resource.getId();
        queuedRequest(type);
        UUID assignmentId = assignmentService.assignNext().assignmentId();
        assignmentService.start(assignmentId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> completion = executor.submit(() -> {
                ready.countDown();
                if (!startGate.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start completion request");
                }
                assignmentService.complete(assignmentId, 12L);
                return null;
            });
            Future<Integer> statusChange = executor.submit(() -> {
                ready.countDown();
                if (!startGate.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start admin status request");
                }
                return mockMvc.perform(patch("/api/resources/{id}/status", resourceId)
                                .with(user("operator").roles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"AVAILABLE\"}"))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            // Release both independent transactional requests together; this exercises their race
            // without claiming which transaction acquires the resource lock first.
            startGate.countDown();

            completion.get(10, TimeUnit.SECONDS);
            assertThat(statusChange.get(10, TimeUnit.SECONDS)).isIn(200, 409);
            assertThat(assignments.findById(assignmentId).orElseThrow().getStatus())
                    .isEqualTo(AssignmentStatus.COMPLETED);
            assertThat(assignments.existsByResourceIdAndStatusIn(
                    resourceId, List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.IN_PROGRESS))).isFalse();
            assertThat(resources.findById(resourceId).orElseThrow().getStatus())
                    .isEqualTo(ResourceStatus.AVAILABLE);
        } finally {
            startGate.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
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
        customers.deleteAllById(customerIds);
        users.deleteAllById(userIds);
        resources.deleteById(resourceId);
        serviceTypes.deleteById(serviceTypeId);
    }

    private void queuedRequest(ServiceType type) {
        Customer customer = CustomerTestFixtures.createPersistedCustomer(users, customers);
        customerIds.add(customer.getId());
        userIds.add(customer.getUser().getId());
        Vehicle vehicle = vehicles.saveAndFlush(Vehicle.create(
                customer, "KA" + UUID.randomUUID().toString().substring(0, 8), "Toyota", "Camry", 2024));
        vehicleIds.add(vehicle.getId());
        ServiceRequestResponse request = serviceRequests.create(new CreateServiceRequestRequest(vehicle.getId(), PriorityClass.NORMAL, null, List.of(type.getId())));
        requestIds.add(request.id());
        workflowIds.add(request.workflowId());
        stageIds.add(request.stages().getFirst().id());
        queue.queueStage(request.stages().getFirst().id());
    }
}
