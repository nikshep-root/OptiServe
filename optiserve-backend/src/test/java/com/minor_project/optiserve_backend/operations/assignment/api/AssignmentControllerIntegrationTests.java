package com.minor_project.optiserve_backend.operations.assignment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.ServiceWorkflowStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequestStatus;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.authentication.domain.AuthUser;
import com.minor_project.optiserve_backend.authentication.persistence.AuthUserRepository;
import com.minor_project.optiserve_backend.customer.CustomerTestFixtures;
import com.minor_project.optiserve_backend.customer.domain.Customer;
import com.minor_project.optiserve_backend.customer.persistence.CustomerRepository;
import com.minor_project.optiserve_backend.operations.persistence.AssignmentRepository;
import com.minor_project.optiserve_backend.operations.persistence.QueueEntryRepository;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceStageRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import com.minor_project.optiserve_backend.operations.persistence.VehicleRepository;
import com.minor_project.optiserve_backend.operations.scheduler.persistence.SchedulingDecisionRepository;
import com.minor_project.optiserve_backend.operations.scheduler.persistence.SchedulingCandidateEvaluationRepository;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingDecisionResult;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingTrigger;
import com.minor_project.optiserve_backend.operations.queue.application.QueueApplicationService;
import com.minor_project.optiserve_backend.operations.assignment.application.AssignmentApplicationService;
import com.minor_project.optiserve_backend.operations.servicerequest.api.CreateServiceRequestRequest;
import com.minor_project.optiserve_backend.operations.servicerequest.api.ServiceRequestResponse;
import com.minor_project.optiserve_backend.operations.servicerequest.application.ServiceRequestApplicationService;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
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
    @Autowired private AuthUserRepository users;
    @Autowired private CustomerRepository customers;
    @Autowired private ServiceTypeRepository serviceTypes;
    @Autowired private ResourceRepository resources;
    @Autowired private ServiceStageRepository stages;
    @Autowired private QueueEntryRepository queueEntries;
    @Autowired private AssignmentRepository assignments;
    @Autowired private ServiceRequestApplicationService serviceRequests;
    @Autowired private QueueApplicationService queue;
    @Autowired private AssignmentApplicationService assignmentService;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SchedulingDecisionRepository schedulingDecisions;
    @Autowired private SchedulingCandidateEvaluationRepository schedulingCandidates;

    @Test
    void customerCannotTriggerStaffAssignmentActions() throws Exception {
        mockMvc.perform(post("/api/assignments/next").with(user("customer@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignmentListIsScopedToCustomerAndAvailableWorkshopWideToAdmins() throws Exception {
        String firstCustomer = "first-customer@example.com";
        String secondCustomer = "second-customer@example.com";
        UUID firstAssignment = assignedFor(firstCustomer);
        UUID secondAssignment = assignedFor(secondCustomer);

        mockMvc.perform(get("/api/assignments").with(user(firstCustomer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assignmentId").value(firstAssignment.toString()))
                .andExpect(jsonPath("$[1]").doesNotExist());

        mockMvc.perform(get("/api/assignments").with(user("operator").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assignmentId").exists())
                .andExpect(jsonPath("$[1].assignmentId").exists());

        assertThat(firstAssignment).isNotEqualTo(secondAssignment);
    }

    @Test
    void assignmentListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/assignments")).andExpect(status().isUnauthorized());
    }

    @Test
    void assignsNextStageAndReturnsAssignmentDetails() throws Exception {
        ServiceRequestResponse request = queuedRequest();
        Resource resource = resources.saveAndFlush(Resource.create("Inspection Bay", Set.of(serviceType())));

        mockMvc.perform(post("/api/assignments/next").with(user("operator").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignmentId").exists())
                .andExpect(jsonPath("$.stageId").value(request.stages().getFirst().id().toString()))
                .andExpect(jsonPath("$.resourceId").value(resource.getId().toString()))
                .andExpect(jsonPath("$.resourceName").value("Inspection Bay"))
                .andExpect(jsonPath("$.assignmentStatus").value("ASSIGNED"));

        var decision = schedulingDecisions.findAll().stream()
                .max(java.util.Comparator.comparing(com.minor_project.optiserve_backend.operations.scheduler.persistence.SchedulingDecision::getEvaluatedAt))
                .orElseThrow();
        assertThat(decision.getTrigger()).isEqualTo(SchedulingTrigger.MANUAL_ASSIGN_NEXT);
        assertThat(decision.getResult()).isEqualTo(SchedulingDecisionResult.ASSIGNED);
        assertThat(decision.getSelectedStageId()).isEqualTo(request.stages().getFirst().id());
        assertThat(schedulingCandidates.findByDecisionIdOrderByEvaluationOrderAsc(decision.getId()))
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.getOutcome()).hasToString("SELECTED");
                    assertThat(candidate.getServiceStageId()).isEqualTo(request.stages().getFirst().id());
                });

        assertThat(stages.findById(request.stages().getFirst().id()).orElseThrow().getStatus())
                .isEqualTo(ServiceStageStatus.ASSIGNED);
        assertThat(resources.findById(resource.getId()).orElseThrow().getStatus()).isEqualTo(ResourceStatus.BUSY);
        assertThat(queueEntries.findByServiceStageIdAndStatus(request.stages().getFirst().id(), QueueEntryStatus.WAITING))
                .isEmpty();
        assertThat(assignments.count()).isEqualTo(1);
    }

    @Test
    void returnsCleanEmptyResultsForNoQueueAndNoCompatibleResource() throws Exception {
        mockMvc.perform(post("/api/assignments/next").with(user("operator").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NO_QUEUED_STAGE"))
                .andExpect(jsonPath("$.assignmentId").doesNotExist());

        ServiceRequestResponse request = queuedRequest();
        mockMvc.perform(post("/api/assignments/next").with(user("operator").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NO_COMPATIBLE_RESOURCE"));
        assertThat(stages.findById(request.stages().getFirst().id()).orElseThrow().getStatus())
                .isEqualTo(ServiceStageStatus.QUEUED);
        assertThat(queueEntries.findByServiceStageIdAndStatus(request.stages().getFirst().id(), QueueEntryStatus.WAITING))
                .isPresent();
    }

    @Test
    void unassignableCriticalCandidateDoesNotBlockAssignableNormalCandidate() {
        ServiceType criticalType = serviceTypes.saveAndFlush(
                ServiceType.create("Critical only " + UUID.randomUUID(), null, Duration.ofMinutes(15)));
        ServiceType normalType = serviceTypes.saveAndFlush(
                ServiceType.create("Normal compatible " + UUID.randomUUID(), null, Duration.ofMinutes(15)));
        ServiceRequestResponse criticalRequest = queuedRequest(PriorityClass.CRITICAL, criticalType);
        ServiceRequestResponse normalRequest = queuedRequest(PriorityClass.NORMAL, normalType);
        QueueEntry criticalEntry = queueEntries.findByServiceStageIdAndStatus(
                criticalRequest.stages().getFirst().id(), QueueEntryStatus.WAITING).orElseThrow();
        var originalQueuedAt = criticalEntry.getQueuedAt();
        resources.saveAndFlush(Resource.create("Normal compatible bay " + UUID.randomUUID(), Set.of(normalType)));

        var result = assignmentService.assignNext();

        assertThat(result.result()).isEqualTo(com.minor_project.optiserve_backend.operations.assignment.api.AssignmentNextResult.ASSIGNED);
        assertThat(result.serviceRequestId()).isEqualTo(normalRequest.id());
        assertThat(stages.findById(criticalRequest.stages().getFirst().id()).orElseThrow().getStatus())
                .isEqualTo(ServiceStageStatus.QUEUED);
        QueueEntry remainingCritical = queueEntries.findByServiceStageIdAndStatus(
                criticalRequest.stages().getFirst().id(), QueueEntryStatus.WAITING).orElseThrow();
        assertThat(remainingCritical.getId()).isEqualTo(criticalEntry.getId());
        assertThat(remainingCritical.getQueuedAt()).isEqualTo(originalQueuedAt);
        assertThat(assignments.findAllByOrderByAssignedAtDesc()).filteredOn(
                assignment -> assignment.getServiceRequest().getId().equals(criticalRequest.id())).isEmpty();
    }

    @Test
    void compatibleCriticalCandidateStillWinsOverAssignableNormalCandidate() {
        ServiceType criticalType = serviceTypes.saveAndFlush(
                ServiceType.create("Critical compatible " + UUID.randomUUID(), null, Duration.ofMinutes(15)));
        ServiceType normalType = serviceTypes.saveAndFlush(
                ServiceType.create("Normal also compatible " + UUID.randomUUID(), null, Duration.ofMinutes(15)));
        ServiceRequestResponse criticalRequest = queuedRequest(PriorityClass.CRITICAL, criticalType);
        ServiceRequestResponse normalRequest = queuedRequest(PriorityClass.NORMAL, normalType);
        resources.saveAndFlush(Resource.create("Critical bay " + UUID.randomUUID(), Set.of(criticalType)));
        resources.saveAndFlush(Resource.create("Normal bay " + UUID.randomUUID(), Set.of(normalType)));

        var result = assignmentService.assignNext();

        assertThat(result.result()).isEqualTo(com.minor_project.optiserve_backend.operations.assignment.api.AssignmentNextResult.ASSIGNED);
        assertThat(result.serviceRequestId()).isEqualTo(criticalRequest.id());
        assertThat(stages.findById(normalRequest.stages().getFirst().id()).orElseThrow().getStatus())
                .isEqualTo(ServiceStageStatus.QUEUED);
        assertThat(queueEntries.findByServiceStageIdAndStatus(
                normalRequest.stages().getFirst().id(), QueueEntryStatus.WAITING)).isPresent();
    }

    @Test
    void allUnassignableCandidatesRemainQueuedWithoutCreatingAssignments() {
        ServiceType criticalType = serviceTypes.saveAndFlush(
                ServiceType.create("Unassignable critical " + UUID.randomUUID(), null, Duration.ofMinutes(15)));
        ServiceType normalType = serviceTypes.saveAndFlush(
                ServiceType.create("Unassignable normal " + UUID.randomUUID(), null, Duration.ofMinutes(15)));
        ServiceRequestResponse criticalRequest = queuedRequest(PriorityClass.CRITICAL, criticalType);
        ServiceRequestResponse normalRequest = queuedRequest(PriorityClass.NORMAL, normalType);
        QueueEntry criticalEntry = queueEntries.findByServiceStageIdAndStatus(
                criticalRequest.stages().getFirst().id(), QueueEntryStatus.WAITING).orElseThrow();
        QueueEntry normalEntry = queueEntries.findByServiceStageIdAndStatus(
                normalRequest.stages().getFirst().id(), QueueEntryStatus.WAITING).orElseThrow();
        long previousAssignmentCount = assignments.count();

        var result = assignmentService.assignNext();

        assertThat(result.result()).isEqualTo(com.minor_project.optiserve_backend.operations.assignment.api.AssignmentNextResult.NO_COMPATIBLE_RESOURCE);
        assertThat(assignments.count()).isEqualTo(previousAssignmentCount);
        assertThat(stages.findById(criticalRequest.stages().getFirst().id()).orElseThrow().getStatus())
                .isEqualTo(ServiceStageStatus.QUEUED);
        assertThat(stages.findById(normalRequest.stages().getFirst().id()).orElseThrow().getStatus())
                .isEqualTo(ServiceStageStatus.QUEUED);
        assertThat(queueEntries.findByServiceStageIdAndStatus(
                criticalRequest.stages().getFirst().id(), QueueEntryStatus.WAITING).orElseThrow().getId())
                .isEqualTo(criticalEntry.getId());
        assertThat(queueEntries.findByServiceStageIdAndStatus(
                normalRequest.stages().getFirst().id(), QueueEntryStatus.WAITING).orElseThrow().getId())
                .isEqualTo(normalEntry.getId());
    }

    @Test
    void startsAssignmentAndReturnsConflictForRepeatedOrCompletedStart() throws Exception {
        UUID assignmentId = assigned();
        mockMvc.perform(post("/api/assignments/{id}/start", assignmentId).with(user("operator").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignmentStatus").value("IN_PROGRESS"));
        mockMvc.perform(post("/api/assignments/{id}/start", assignmentId).with(user("operator").roles("ADMIN")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/assignments/{id}/complete", assignmentId).with(user("operator").roles("ADMIN"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/assignments/{id}/start", assignmentId).with(user("operator").roles("ADMIN")))
                .andExpect(status().isConflict());
    }

    @Test
    void returnsExpectedErrorsForStartAndCompleteEndpoints() throws Exception {
        mockMvc.perform(post("/api/assignments/{id}/start", UUID.randomUUID()).with(user("operator").roles("ADMIN")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/assignments/{id}/complete", UUID.randomUUID()).with(user("operator").roles("ADMIN"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        UUID assignmentId = assigned();
        mockMvc.perform(post("/api/assignments/{id}/complete", assignmentId).with(user("operator").roles("ADMIN"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/assignments/{id}/complete", assignmentId).with(user("operator").roles("ADMIN"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"actualDurationMinutes\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/assignments/{id}/complete", assignmentId).with(user("operator").roles("ADMIN"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"actualDurationMinutes\":-1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/assignments/{id}/complete", assignmentId).with(user("operator").roles("ADMIN"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"actualDurationMinutes\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completingAssignmentReturnsAndPersistsDurationInMinutes() throws Exception {
        UUID assignmentId = assigned();
        mockMvc.perform(post("/api/assignments/{id}/start", assignmentId).with(user("operator").roles("ADMIN")))
                .andExpect(status().isOk());
        assignments.flush();
        stages.flush();
        resources.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/assignments/{id}/complete", assignmentId).with(user("operator").roles("ADMIN"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"actualDurationMinutes\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.resourceStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.actualServiceDuration").value("PT12M"));

        mockMvc.perform(get("/api/assignments").with(user("operator").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].predictedServiceDuration").exists())
                .andExpect(jsonPath("$[0].actualServiceDuration").value("PT12M"))
                .andExpect(jsonPath("$[0].predictionSource").value("DEFAULT_FALLBACK"));

        var assignment = assignments.findById(assignmentId).orElseThrow();
        assertThat(assignment.getActualServiceDuration()).isEqualTo(Duration.ofMinutes(12));
        assertThat(assignment.getCompletedAt()).isNotNull();
        assertThat(assignment.getServiceStage().getWorkflow().getStatus())
                .isEqualTo(ServiceWorkflowStatus.COMPLETED);
        assertThat(assignment.getServiceRequest().getStatus()).isEqualTo(ServiceRequestStatus.COMPLETED);
        assertThat(assignment.getServiceRequest().getActualServiceDuration()).isEqualTo(Duration.ofMinutes(12));
        assertThat(assignment.getResource().getStatus()).isEqualTo(ResourceStatus.AVAILABLE);
    }

    @Test
    void completingNonFinalStageActivatesTheNextStageAndKeepsWorkflowActive() throws Exception {
        ServiceType firstType = serviceTypes.saveAndFlush(
                ServiceType.create("First stage " + UUID.randomUUID(), null, Duration.ofMinutes(15)));
        ServiceType secondType = serviceTypes.saveAndFlush(
                ServiceType.create("Second stage " + UUID.randomUUID(), null, Duration.ofMinutes(20)));
        Vehicle vehicle = vehicles.saveAndFlush(Vehicle.create(
                CustomerTestFixtures.createPersistedCustomer(users, customers),
                "KA" + UUID.randomUUID().toString().substring(0, 8), "Toyota", "Camry", 2024));
        ServiceRequestResponse request = serviceRequests.create(new CreateServiceRequestRequest(
                vehicle.getId(), PriorityClass.NORMAL, null, List.of(firstType.getId(), secondType.getId())));
        UUID firstStageId = request.stages().get(0).id();
        UUID secondStageId = request.stages().get(1).id();
        queue.queueStage(firstStageId);
        resources.saveAndFlush(Resource.create("First bay " + UUID.randomUUID(), Set.of(firstType)));
        UUID assignmentId = assignmentService.assignNext().assignmentId();

        mockMvc.perform(post("/api/assignments/{id}/start", assignmentId).with(user("operator").roles("ADMIN")))
                .andExpect(status().isOk());
        assignments.flush();
        stages.flush();
        resources.flush();
        entityManager.clear();
        mockMvc.perform(post("/api/assignments/{id}/complete", assignmentId).with(user("operator").roles("ADMIN"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"actualDurationMinutes\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualServiceDuration").value("PT12M"));

        assertThat(stages.findById(firstStageId).orElseThrow().getStatus()).isEqualTo(ServiceStageStatus.COMPLETED);
        assertThat(stages.findById(secondStageId).orElseThrow().getStatus()).isEqualTo(ServiceStageStatus.ELIGIBLE);
        var completed = assignments.findById(assignmentId).orElseThrow();
        assertThat(completed.getServiceStage().getWorkflow().getStatus()).isEqualTo(ServiceWorkflowStatus.ACTIVE);
        assertThat(completed.getServiceRequest().getStatus()).isNotEqualTo(ServiceRequestStatus.COMPLETED);
    }

    @Test
    void activeAssignmentCannotBeMarkedAvailableAndOfflineStateSurvivesCompletion() throws Exception {
        ServiceRequestResponse request = queuedRequest();
        ServiceType type = stages.findById(request.stages().getFirst().id()).orElseThrow().getServiceType();
        Resource resource = resources.saveAndFlush(Resource.create(
                "Status invariant bay " + UUID.randomUUID(), Set.of(type)));
        UUID assignmentId = assignmentService.assignNext().assignmentId();

        mockMvc.perform(patch("/api/resources/{id}/status", resource.getId())
                        .with(user("operator").roles("ADMIN"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The request conflicts with existing data."));
        assertThat(resources.findById(resource.getId()).orElseThrow().getStatus()).isEqualTo(ResourceStatus.BUSY);

        mockMvc.perform(post("/api/assignments/{id}/start", assignmentId).with(user("operator").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/resources/{id}/status", resource.getId())
                        .with(user("operator").roles("ADMIN"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/resources/{id}/status", resource.getId())
                        .with(user("operator").roles("ADMIN"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFLINE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFLINE"));
        mockMvc.perform(post("/api/assignments/{id}/complete", assignmentId).with(user("operator").roles("ADMIN"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"actualDurationMinutes\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceStatus").value("OFFLINE"));

        mockMvc.perform(patch("/api/resources/{id}/status", resource.getId())
                        .with(user("operator").roles("ADMIN"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        assertThat(assignments.findById(assignmentId).orElseThrow().getStatus().name()).isEqualTo("COMPLETED");
    }

    @Test
    void failedCompletionRollsBackAllLifecycleChanges() throws Exception {
        ServiceType firstType = serviceTypes.saveAndFlush(
                ServiceType.create("Rollback first " + UUID.randomUUID(), null, Duration.ofMinutes(15)));
        ServiceType secondType = serviceTypes.saveAndFlush(
                ServiceType.create("Rollback second " + UUID.randomUUID(), null, Duration.ofMinutes(20)));
        Vehicle vehicle = vehicles.saveAndFlush(Vehicle.create(
                CustomerTestFixtures.createPersistedCustomer(users, customers),
                "KA" + UUID.randomUUID().toString().substring(0, 8), "Toyota", "Camry", 2024));
        ServiceRequestResponse request = serviceRequests.create(new CreateServiceRequestRequest(
                vehicle.getId(), PriorityClass.NORMAL, null, List.of(firstType.getId(), secondType.getId())));
        UUID firstStageId = request.stages().get(0).id();
        UUID secondStageId = request.stages().get(1).id();
        queue.queueStage(firstStageId);
        resources.saveAndFlush(Resource.create("Rollback bay " + UUID.randomUUID(), Set.of(firstType)));
        UUID assignmentId = assignmentService.assignNext().assignmentId();
        assignmentService.start(assignmentId);
        assignments.flush();
        stages.flush();
        resources.flush();

        jdbcTemplate.update("UPDATE service_stages SET status = 'CANCELLED' WHERE id = ?", secondStageId);
        entityManager.clear();

        mockMvc.perform(post("/api/assignments/{id}/complete", assignmentId).with(user("operator").roles("ADMIN"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"actualDurationMinutes\":12}"))
                .andExpect(status().isConflict());

        entityManager.clear();
        var unchanged = assignments.findById(assignmentId).orElseThrow();
        assertThat(unchanged.getStatus().name()).isEqualTo("IN_PROGRESS");
        assertThat(unchanged.getActualServiceDuration()).isNull();
        assertThat(unchanged.getCompletedAt()).isNull();
        assertThat(unchanged.getServiceStage().getStatus()).isEqualTo(ServiceStageStatus.IN_PROGRESS);
        assertThat(stages.findById(secondStageId).orElseThrow().getStatus()).isEqualTo(ServiceStageStatus.CANCELLED);
        assertThat(unchanged.getServiceStage().getWorkflow().getStatus()).isEqualTo(ServiceWorkflowStatus.ACTIVE);
        assertThat(unchanged.getServiceRequest().getStatus()).isEqualTo(ServiceRequestStatus.CREATED);
        assertThat(unchanged.getResource().getStatus()).isEqualTo(ResourceStatus.BUSY);
    }

    private UUID assigned() {
        ServiceRequestResponse request = queuedRequest();
        resources.saveAndFlush(Resource.create("Bay " + UUID.randomUUID(), Set.of(serviceType())));
        return assignmentService.assignNext().assignmentId();
    }

    private UUID assignedFor(String email) {
        ServiceType type = serviceTypes.saveAndFlush(ServiceType.create("Inspection " + UUID.randomUUID(), null, Duration.ofMinutes(15)));
        AuthUser user = users.saveAndFlush(AuthUser.create(email, "test-hash"));
        Customer customer = customers.saveAndFlush(Customer.create(user, "Test Customer", null));
        Vehicle vehicle = vehicles.saveAndFlush(Vehicle.create(customer,
                "KA" + UUID.randomUUID().toString().substring(0, 8), "Toyota", "Camry", 2024));
        ServiceRequestResponse request = serviceRequests.create(new CreateServiceRequestRequest(
                vehicle.getId(), PriorityClass.NORMAL, null, List.of(type.getId())));
        queue.queueStage(request.stages().getFirst().id());
        resources.saveAndFlush(Resource.create("Bay " + UUID.randomUUID(), Set.of(type)));
        return assignmentService.assignNext().assignmentId();
    }

    private ServiceRequestResponse queuedRequest() {
        ServiceType type = serviceTypes.saveAndFlush(ServiceType.create("Inspection " + UUID.randomUUID(), null, Duration.ofMinutes(15)));
        Vehicle vehicle = vehicles.saveAndFlush(Vehicle.create(
                CustomerTestFixtures.createPersistedCustomer(users, customers),
                "KA" + UUID.randomUUID().toString().substring(0, 8), "Toyota", "Camry", 2024));
        ServiceRequestResponse request = serviceRequests.create(new CreateServiceRequestRequest(
                vehicle.getId(), PriorityClass.NORMAL, null, List.of(type.getId())));
        queue.queueStage(request.stages().getFirst().id());
        return request;
    }

    private ServiceRequestResponse queuedRequest(PriorityClass priority, ServiceType type) {
        Vehicle vehicle = vehicles.saveAndFlush(Vehicle.create(
                CustomerTestFixtures.createPersistedCustomer(users, customers),
                "KA" + UUID.randomUUID().toString().substring(0, 8), "Toyota", "Camry", 2024));
        ServiceRequestResponse request = serviceRequests.create(new CreateServiceRequestRequest(
                vehicle.getId(), priority, null, List.of(type.getId())));
        queue.queueStage(request.stages().getFirst().id());
        return request;
    }

    private ServiceType serviceType() {
        return serviceTypes.findAll().getFirst();
    }
}
