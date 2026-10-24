package com.minor_project.optiserve_backend.analytics.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minor_project.optiserve_backend.authentication.domain.AuthUser;
import com.minor_project.optiserve_backend.authentication.persistence.AuthUserRepository;
import com.minor_project.optiserve_backend.customer.domain.Customer;
import com.minor_project.optiserve_backend.customer.persistence.CustomerRepository;
import com.minor_project.optiserve_backend.operations.assignment.application.AssignmentApplicationService;
import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import com.minor_project.optiserve_backend.operations.persistence.VehicleRepository;
import com.minor_project.optiserve_backend.operations.queue.application.QueueApplicationService;
import com.minor_project.optiserve_backend.operations.servicerequest.api.CreateServiceRequestRequest;
import com.minor_project.optiserve_backend.operations.servicerequest.application.ServiceRequestApplicationService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
class WorkshopAnalyticsControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthUserRepository users;
    @Autowired private CustomerRepository customers;
    @Autowired private VehicleRepository vehicles;
    @Autowired private ServiceTypeRepository serviceTypes;
    @Autowired private ResourceRepository resources;
    @Autowired private ServiceRequestApplicationService serviceRequests;
    @Autowired private QueueApplicationService queue;
    @Autowired private AssignmentApplicationService assignments;

    @Test
    void analyticsIsAdminOnlyAndDoesNotExposePersonalInformation() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        mockMvc.perform(get("/api/admin/analytics").param("from", today.toString()).param("to", today.toString())
                        .with(user("customer@example.com")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/analytics").param("from", today.toString()).param("to", today.toString())
                        .with(user("operator").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historical").exists())
                .andExpect(jsonPath("$.current").exists())
                .andExpect(jsonPath("$.completionTrend").isArray())
                .andExpect(jsonPath("$.completionTrend[0].date").value(today.toString()))
                .andExpect(jsonPath("$.customer").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void rangeValidationRejectsReversedAndOverlongPeriods() throws Exception {
        mockMvc.perform(get("/api/admin/analytics").param("from", "2026-05-02").param("to", "2026-05-01")
                        .with(user("operator").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/analytics").param("from", "not-a-date").param("to", "2026-05-01")
                        .with(user("operator").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/analytics").param("from", "2026-05-01")
                        .with(user("operator").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/analytics").param("from", "2025-01-01").param("to", "2026-01-02")
                        .with(user("operator").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completedMetricsExcludeInProgressWorkAndLiveCountsRemainCurrent() throws Exception {
        ServiceType completedType = createType();
        UUID completedStage = createQueuedRequest(completedType);
        Resource completedResource = resources.saveAndFlush(Resource.create("Completed bay " + UUID.randomUUID(), Set.of(completedType)));
        UUID completedAssignment = assignments.assignNext().assignmentId();
        assignments.start(completedAssignment);
        assignments.complete(completedAssignment, 20L);

        ServiceType activeType = createType();
        UUID activeStage = createQueuedRequest(activeType);
        resources.saveAndFlush(Resource.create("Active bay " + UUID.randomUUID(), Set.of(activeType)));
        UUID activeAssignment = assignments.assignNext().assignmentId();
        assignments.start(activeAssignment);

        ServiceType waitingType = createType();
        UUID waitingStage = createQueuedRequest(waitingType);
        resources.saveAndFlush(Resource.create("Offline bay " + UUID.randomUUID(), Set.of(waitingType)));
        Resource offlineResource = resources.findAll().stream()
                .filter(resource -> resource.getName().startsWith("Offline bay"))
                .findFirst().orElseThrow();
        offlineResource.changeStatus(ResourceStatus.OFFLINE);
        resources.saveAndFlush(offlineResource);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        mockMvc.perform(get("/api/admin/analytics").param("from", today.toString()).param("to", today.toString())
                        .with(user("operator").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historical.requestsCreated").value(3))
                .andExpect(jsonPath("$.historical.requestsCompleted").value(1))
                .andExpect(jsonPath("$.historical.averageActualServiceDurationMinutes").value(20.0))
                .andExpect(jsonPath("$.historical.meanAbsolutePredictionErrorMinutes").value(10.0))
                .andExpect(jsonPath("$.historical.predictionErrorSampleCount").value(1))
                .andExpect(jsonPath("$.current.waitingQueueSize").value(1))
                .andExpect(jsonPath("$.current.activeAssignments").value(1))
                .andExpect(jsonPath("$.current.availableResources").value(1))
                .andExpect(jsonPath("$.current.busyResources").value(1))
                .andExpect(jsonPath("$.current.offlineResources").value(1))
                .andExpect(jsonPath("$.completionTrend[0].completedRequests").value(1));

        LocalDate future = today.plusDays(10);
        mockMvc.perform(get("/api/admin/analytics").param("from", future.toString()).param("to", future.toString())
                        .with(user("operator").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historical.requestsCreated").value(0))
                .andExpect(jsonPath("$.historical.requestsCompleted").value(0))
                .andExpect(jsonPath("$.historical.averageActualServiceDurationMinutes").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.historical.meanAbsolutePredictionErrorMinutes").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.historical.predictionErrorSampleCount").value(0))
                .andExpect(jsonPath("$.current.activeAssignments").value(1))
                .andExpect(jsonPath("$.current.waitingQueueSize").value(1))
                .andExpect(jsonPath("$.completionTrend[0].completedRequests").value(0));
    }

    private ServiceType createType() {
        return serviceTypes.saveAndFlush(ServiceType.create("Analytics service " + UUID.randomUUID(), null,
                Duration.ofMinutes(30)));
    }

    private UUID createQueuedRequest(ServiceType type) {
        String email = UUID.randomUUID() + "@example.test";
        var user = users.saveAndFlush(AuthUser.create(email, "test-hash"));
        Customer customer = customers.saveAndFlush(Customer.create(user, "Analytics test customer", null));
        Vehicle vehicle = vehicles.saveAndFlush(Vehicle.create(customer,
                "AN" + UUID.randomUUID().toString().substring(0, 8), "Test", "Vehicle", 2024));
        var request = serviceRequests.create(new CreateServiceRequestRequest(vehicle.getId(), PriorityClass.NORMAL,
                null, List.of(type.getId())));
        UUID stageId = request.stages().getFirst().id();
        queue.queueStage(stageId);
        return stageId;
    }
}
