package com.minor_project.optiserve_backend.operations.automation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minor_project.optiserve_backend.authentication.persistence.AuthUserRepository;
import com.minor_project.optiserve_backend.customer.CustomerTestFixtures;
import com.minor_project.optiserve_backend.customer.domain.Customer;
import com.minor_project.optiserve_backend.customer.persistence.CustomerRepository;
import com.minor_project.optiserve_backend.operations.assignment.application.AssignmentApplicationService;
import com.minor_project.optiserve_backend.operations.automation.application.WorkshopAutomationApplicationService;
import com.minor_project.optiserve_backend.operations.automation.application.WorkshopAutomationCoordinator;
import com.minor_project.optiserve_backend.operations.automation.domain.WorkshopAutomationMode;
import com.minor_project.optiserve_backend.operations.domain.Assignment;
import com.minor_project.optiserve_backend.operations.domain.AssignmentStatus;
import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.ServiceWorkflow;
import com.minor_project.optiserve_backend.operations.domain.ServiceWorkflowStatus;
import com.minor_project.optiserve_backend.operations.persistence.AssignmentRepository;
import com.minor_project.optiserve_backend.operations.persistence.QueueEntryRepository;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceRequestRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceStageRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceWorkflowRepository;
import com.minor_project.optiserve_backend.operations.persistence.VehicleRepository;
import com.minor_project.optiserve_backend.operations.resource.application.ResourceApplicationService;
import com.minor_project.optiserve_backend.operations.queue.application.QueueApplicationService;
import com.minor_project.optiserve_backend.operations.servicerequest.api.CreateServiceRequestRequest;
import com.minor_project.optiserve_backend.operations.servicerequest.api.ServiceRequestResponse;
import com.minor_project.optiserve_backend.operations.servicerequest.application.ServiceRequestApplicationService;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class WorkshopAutomationIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private WorkshopAutomationApplicationService automationSettings;
    @Autowired private WorkshopAutomationCoordinator coordinator;
    @Autowired private ServiceRequestApplicationService serviceRequests;
    @Autowired private AssignmentApplicationService assignments;
    @Autowired private ResourceApplicationService resourceService;
    @Autowired private QueueApplicationService queue;
    @Autowired private ServiceTypeRepository serviceTypes;
    @Autowired private VehicleRepository vehicles;
    @Autowired private AuthUserRepository users;
    @Autowired private CustomerRepository customers;
    @Autowired private ResourceRepository resources;
    @Autowired private AssignmentRepository assignmentRepository;
    @Autowired private QueueEntryRepository queueEntries;
    @Autowired private ServiceStageRepository stages;
    @Autowired private ServiceWorkflowRepository workflows;
    @Autowired private ServiceRequestRepository requests;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<UUID> resourceIds = new ArrayList<>();
    private final List<UUID> serviceTypeIds = new ArrayList<>();
    private final List<UUID> vehicleIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> requestIds = new ArrayList<>();
    private final List<UUID> workflowIds = new ArrayList<>();
    private final List<UUID> stageIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        automationSettings.setMode(WorkshopAutomationMode.MANUAL);
        assignmentRepository.deleteAll(assignmentRepository.findAllByOrderByAssignedAtDesc().stream()
                .filter(assignment -> requestIds.contains(assignment.getServiceRequest().getId())).toList());
        queueEntries.deleteAll(queueEntries.findAll().stream()
                .filter(entry -> requestIds.contains(entry.getServiceRequest().getId())).toList());
        stages.deleteAllById(stageIds);
        workflows.deleteAllById(workflowIds);
        requests.deleteAllById(requestIds);
        vehicles.deleteAllById(vehicleIds);
        customers.deleteAllById(customerIds);
        users.deleteAllById(userIds);
        resources.deleteAllById(resourceIds);
        serviceTypes.deleteAllById(serviceTypeIds);
    }

    @Test
    void settingDefaultsToManualAndOnlyAdminCanReadOrChangeIt() throws Exception {
        assertThat(automationSettings.getMode().mode()).isEqualTo(WorkshopAutomationMode.MANUAL);
        mockMvc.perform(get("/api/admin/workshop-automation").with(user("advisor").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("MANUAL"));
        mockMvc.perform(get("/api/admin/workshop-automation").with(user("customer").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/workshop-automation").with(user("customer").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"AUTO\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/workshop-automation").with(user("advisor").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(automationSettings.getMode().mode()).isEqualTo(WorkshopAutomationMode.MANUAL);

        ServiceType type = serviceType("Manual only");
        ServiceRequestResponse request = request(List.of(type), PriorityClass.NORMAL);
        assertThat(request.stages().getFirst().status()).isEqualTo(ServiceStageStatus.ELIGIBLE);
        assertThat(waitingEntries()).isEmpty();

        mockMvc.perform(put("/api/admin/workshop-automation").with(user("advisor").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"AUTO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("AUTO"));
        assertThat(automationSettings.getMode().mode()).isEqualTo(WorkshopAutomationMode.AUTO);
        assertThat(waitingEntries()).hasSize(1);
    }

    @Test
    void autoQueuesAndAssignsNewEligibleWorkToCompatibleAvailableResource() {
        ServiceType type = serviceType("Automatic inspection");
        Resource bay = resource("Compatible bay", Set.of(type));
        automationSettings.setMode(WorkshopAutomationMode.AUTO);

        ServiceRequestResponse request = request(List.of(type), PriorityClass.NORMAL);

        assertThat(request.stages().getFirst().status()).isEqualTo(ServiceStageStatus.ELIGIBLE);
        Assignment assignment = assignmentFor(request.id());
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assignment.getResource().getId()).isEqualTo(bay.getId());
        assertThat(queueEntries.findByServiceStageIdAndStatus(request.stages().getFirst().id(), QueueEntryStatus.WAITING))
                .isEmpty();
        assertThat(resources.findById(bay.getId()).orElseThrow().getStatus()).isEqualTo(ResourceStatus.BUSY);
    }

    @Test
    void unavailableResourceKeepsWorkQueuedAndCompatibilityChangeRetriesIt() {
        ServiceType type = serviceType("Deferred service");
        automationSettings.setMode(WorkshopAutomationMode.AUTO);
        ServiceRequestResponse request = request(List.of(type), PriorityClass.NORMAL);
        assertThat(waitingEntries()).hasSize(1);
        assertThat(assignmentForRequest(request.id())).isEmpty();
        assertThat(stage(request).getStatus()).isEqualTo(ServiceStageStatus.QUEUED);

        Resource bay = resource("Later compatible bay", Set.of());
        resourceService.addCompatibility(bay.getId(), type.getId());

        assertThat(assignmentForRequest(request.id())).hasSize(1);
        assertThat(waitingEntries()).isEmpty();
    }

    @Test
    void autoReconciliationSkipsUnassignableCriticalWorkWithoutDroppingIt() {
        ServiceType criticalType = serviceType("Auto critical without bay");
        ServiceType normalType = serviceType("Auto normal with bay");
        Resource normalBay = resource("Auto normal compatible bay", Set.of(normalType));
        automationSettings.setMode(WorkshopAutomationMode.AUTO);

        ServiceRequestResponse critical = request(List.of(criticalType), PriorityClass.CRITICAL);
        QueueEntry criticalEntry = queueEntries.findByServiceStageIdAndStatus(
                critical.stages().getFirst().id(), QueueEntryStatus.WAITING).orElseThrow();
        ServiceRequestResponse normal = request(List.of(normalType), PriorityClass.NORMAL);

        Assignment normalAssignment = assignmentFor(normal.id());
        assertThat(normalAssignment.getResource().getId()).isEqualTo(normalBay.getId());
        assertThat(stage(normal).getStatus()).isEqualTo(ServiceStageStatus.ASSIGNED);
        assertThat(stage(critical).getStatus()).isEqualTo(ServiceStageStatus.QUEUED);
        QueueEntry remainingCritical = queueEntries.findByServiceStageIdAndStatus(
                critical.stages().getFirst().id(), QueueEntryStatus.WAITING).orElseThrow();
        assertThat(remainingCritical.getId()).isEqualTo(criticalEntry.getId());
        assertThat(assignmentForRequest(critical.id())).isEmpty();
    }

    @Test
    void completionQueuesOnlyImmediateSuccessorAndCompletesFinalWorkflow() {
        ServiceType firstType = serviceType("Initial inspection");
        ServiceType secondType = serviceType("Repair");
        ServiceType thirdType = serviceType("Quality check");
        Resource firstBay = resource("Inspection and repair bay", Set.of(firstType, secondType));
        resource("Quality check bay", Set.of(thirdType));
        automationSettings.setMode(WorkshopAutomationMode.AUTO);
        ServiceRequestResponse request = request(List.of(firstType, secondType, thirdType), PriorityClass.NORMAL);

        Assignment first = assignmentFor(request.id());
        assertThat(first.getResource().getId()).isEqualTo(firstBay.getId());
        assignments.start(first.getId());
        assignments.complete(first.getId(), 22L);

        List<ServiceStage> persistedStages = stages.findByWorkflowIdOrderBySequenceNumberAsc(request.workflowId());
        assertThat(persistedStages).extracting(ServiceStage::getStatus)
                .containsExactly(ServiceStageStatus.COMPLETED, ServiceStageStatus.ASSIGNED, ServiceStageStatus.PENDING);
        Assignment second = assignmentRepository.findAllByOrderByAssignedAtDesc().stream()
                .filter(assignment -> assignment.getServiceRequest().getId().equals(request.id()))
                .filter(assignment -> assignment.getServiceStage().getSequenceNumber() == 2).findFirst().orElseThrow();
        assertThat(second.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);

        assignments.start(second.getId());
        assignments.complete(second.getId(), 35L);
        assertThat(stages.findByWorkflowIdOrderBySequenceNumberAsc(request.workflowId()))
                .extracting(ServiceStage::getStatus)
                .containsExactly(ServiceStageStatus.COMPLETED, ServiceStageStatus.COMPLETED, ServiceStageStatus.ASSIGNED);
        assertThat(workflows.findByServiceRequestId(request.id()).orElseThrow().getStatus())
                .isEqualTo(ServiceWorkflowStatus.ACTIVE);
        Assignment third = assignmentRepository.findAllByOrderByAssignedAtDesc().stream()
                .filter(assignment -> assignment.getServiceRequest().getId().equals(request.id()))
                .filter(assignment -> assignment.getServiceStage().getSequenceNumber() == 3).findFirst().orElseThrow();
        assignments.start(third.getId());
        assignments.complete(third.getId(), 41L);
        assertThat(workflows.findByServiceRequestId(request.id()).orElseThrow().getStatus())
                .isEqualTo(ServiceWorkflowStatus.COMPLETED);
        assertThat(requests.findById(request.id()).orElseThrow().getStatus().name()).isEqualTo("COMPLETED");
        assertThat(waitingEntries()).isEmpty();
        assertThat(resources.findById(firstBay.getId()).orElseThrow().getStatus()).isEqualTo(ResourceStatus.AVAILABLE);
    }

    @Test
    void criticalWorkWaitsWithoutInterruptingInProgressServiceAndConcurrentTriggersStayIdempotent() throws Exception {
        ServiceType type = serviceType("Priority service");
        Resource bay = resource("Single priority bay", Set.of(type));
        automationSettings.setMode(WorkshopAutomationMode.AUTO);
        ServiceRequestResponse normal = request(List.of(type), PriorityClass.NORMAL);
        Assignment active = assignmentFor(normal.id());
        assignments.start(active.getId());

        ServiceRequestResponse critical = request(List.of(type), PriorityClass.CRITICAL);
        assertThat(assignmentRepository.findById(active.getId()).orElseThrow().getStatus())
                .isEqualTo(AssignmentStatus.IN_PROGRESS);
        assertThat(stage(critical).getStatus()).isEqualTo(ServiceStageStatus.QUEUED);
        assertThat(assignmentForRequest(critical.id())).isEmpty();
        assertThat(resources.findById(bay.getId()).orElseThrow().getStatus()).isEqualTo(ResourceStatus.BUSY);

        assignments.complete(active.getId(), 18L);
        Assignment criticalAssignment = assignmentFor(critical.id());
        assertThat(criticalAssignment.getServiceRequest().getPriorityClass()).isEqualTo(PriorityClass.CRITICAL);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            var first = executor.submit(() -> { barrier.await(); coordinator.reconcile(); return null; });
            var second = executor.submit(() -> { barrier.await(); coordinator.reconcile(); return null; });
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }
        assertThat(assignmentForRequest(critical.id())).hasSize(1);
        assertThat(assignmentRepository.findAllByOrderByAssignedAtDesc().stream().filter(item -> item.getResource().getId().equals(bay.getId()))
                .filter(item -> item.getStatus() == AssignmentStatus.ASSIGNED || item.getStatus() == AssignmentStatus.IN_PROGRESS))
                .hasSize(1);
    }

    @Test
    void switchingBackToManualStopsAutomaticActionsAndRetainsWaitingWork() {
        ServiceType type = serviceType("Manual switch service");
        automationSettings.setMode(WorkshopAutomationMode.AUTO);
        ServiceRequestResponse request = request(List.of(type), PriorityClass.NORMAL);
        assertThat(waitingEntries()).hasSize(1);

        automationSettings.setMode(WorkshopAutomationMode.MANUAL);
        Resource bay = resource("After switch bay", Set.of());
        resourceService.addCompatibility(bay.getId(), type.getId());

        assertThat(waitingEntries()).hasSize(1);
        assertThat(stage(request).getStatus()).isEqualTo(ServiceStageStatus.QUEUED);
        assertThat(assignmentForRequest(request.id())).isEmpty();
    }

    @Test
    void enablingAutoPreservesExistingWaitingEntryWhenNoResourceCanTakeIt() {
        ServiceType type = serviceType("Pre-existing queue work");
        ServiceRequestResponse request = request(List.of(type), PriorityClass.NORMAL);
        var existingEntry = queueEntries.findByServiceStageIdAndStatus(request.stages().getFirst().id(), QueueEntryStatus.WAITING);
        assertThat(existingEntry).isEmpty();
        queue.queueStage(request.stages().getFirst().id());
        QueueEntry queued = queueEntries.findByServiceStageIdAndStatus(request.stages().getFirst().id(), QueueEntryStatus.WAITING).orElseThrow();

        automationSettings.setMode(WorkshopAutomationMode.AUTO);

        QueueEntry afterEnable = queueEntries.findByServiceStageIdAndStatus(request.stages().getFirst().id(), QueueEntryStatus.WAITING)
                .orElseThrow();
        assertThat(afterEnable.getId()).isEqualTo(queued.getId());
        assertThat(assignmentForRequest(request.id())).isEmpty();
    }

    @Test
    void rolledBackRequestDoesNotTriggerAutomationOrPersistOperationalRows() {
        ServiceType type = serviceType("Rolled back request");
        automationSettings.setMode(WorkshopAutomationMode.AUTO);
        long requestCount = requests.count();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            request(List.of(type), PriorityClass.NORMAL);
            throw new IllegalStateException("test rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(requests.count()).isEqualTo(requestCount);
        assertThat(waitingEntries()).isEmpty();
        assertThat(assignmentRepository.findAllByOrderByAssignedAtDesc()).noneMatch(assignment -> requestIds.contains(assignment.getServiceRequest().getId()));
    }

    private ServiceType serviceType(String name) {
        ServiceType type = serviceTypes.saveAndFlush(ServiceType.create(name + " " + UUID.randomUUID(), null, Duration.ofMinutes(24)));
        serviceTypeIds.add(type.getId());
        return type;
    }

    private Resource resource(String name, Set<ServiceType> compatibleTypes) {
        Resource resource = resources.saveAndFlush(Resource.create(name + " " + UUID.randomUUID(), compatibleTypes));
        resourceIds.add(resource.getId());
        return resource;
    }

    private ServiceRequestResponse request(List<ServiceType> types, PriorityClass priority) {
        Customer customer = CustomerTestFixtures.createPersistedCustomer(users, customers);
        customerIds.add(customer.getId());
        userIds.add(customer.getUser().getId());
        Vehicle vehicle = vehicles.saveAndFlush(Vehicle.create(customer,
                "KA" + UUID.randomUUID().toString().substring(0, 8), "Toyota", "Camry", 2024));
        vehicleIds.add(vehicle.getId());
        ServiceRequestResponse response = serviceRequests.create(new CreateServiceRequestRequest(
                vehicle.getId(), priority, null, types.stream().map(ServiceType::getId).toList()));
        requestIds.add(response.id());
        workflowIds.add(response.workflowId());
        response.stages().forEach(stage -> stageIds.add(stage.id()));
        return response;
    }

    private ServiceStage stage(ServiceRequestResponse request) {
        return stages.findById(request.stages().getFirst().id()).orElseThrow();
    }

    private Assignment assignmentFor(UUID requestId) {
        return assignmentForRequest(requestId).stream().findFirst().orElseThrow();
    }

    private List<Assignment> assignmentForRequest(UUID requestId) {
        return assignmentRepository.findAllByOrderByAssignedAtDesc().stream()
                .filter(assignment -> requestId.equals(assignment.getServiceRequest().getId())).toList();
    }

    private List<QueueEntry> waitingEntries() {
        return queueEntries.findByStatusOrderByQueuedAtAsc(QueueEntryStatus.WAITING);
    }
}
