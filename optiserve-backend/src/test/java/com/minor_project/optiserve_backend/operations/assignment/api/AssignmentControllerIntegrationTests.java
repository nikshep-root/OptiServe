package com.minor_project.optiserve_backend.operations.assignment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.minor_project.optiserve_backend.operations.queue.application.QueueApplicationService;
import com.minor_project.optiserve_backend.operations.assignment.application.AssignmentApplicationService;
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

    private ServiceType serviceType() {
        return serviceTypes.findAll().getFirst();
    }
}
