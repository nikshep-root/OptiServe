package com.minor_project.optiserve_backend.operations.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minor_project.optiserve_backend.operations.domain.Assignment;
import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.ServiceWorkflow;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.operations.assignment.application.AssignmentApplicationService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OperationsPersistenceIntegrationTests {

    @Autowired private ServiceTypeRepository serviceTypes;
    @Autowired private ResourceRepository resources;
    @Autowired private ServiceRequestRepository requests;
    @Autowired private VehicleRepository vehicles;
    @Autowired private ServiceWorkflowRepository workflows;
    @Autowired private ServiceStageRepository stages;
    @Autowired private QueueEntryRepository queueEntries;
    @Autowired private AssignmentRepository assignments;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AssignmentApplicationService assignmentApplicationService;

    @Test
    void flywayV4CreatesWorkflowSchemaAndHibernatePersistsOrderedStages() {
        ServiceType type = serviceTypes.saveAndFlush(type("Inspection"));
        ServiceRequest request = requests.saveAndFlush(request(type, PriorityClass.URGENT));
        ServiceWorkflow workflow = ServiceWorkflow.create(request);
        ServiceStage first = workflow.addStage(type, Duration.ofMinutes(15));
        workflow.addStage(type, Duration.ofMinutes(20));
        workflows.saveAndFlush(workflow);
        queueEntries.saveAndFlush(QueueEntry.enter(first, Instant.parse("2026-09-08T09:00:00Z")));

        assertThat(stages.findByWorkflowIdOrderBySequenceNumberAsc(workflow.getId()))
                .extracting(ServiceStage::getSequenceNumber, ServiceStage::getStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, ServiceStageStatus.QUEUED),
                        org.assertj.core.groups.Tuple.tuple(2, ServiceStageStatus.PENDING));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '4' AND success", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_name IN ('service_workflows', 'service_stages')", Integer.class)).isEqualTo(2);
    }

    @Test
    void workflowHasOneUniqueRequest() {
        ServiceType type = serviceTypes.saveAndFlush(type("Diagnostics"));
        ServiceRequest request = requests.saveAndFlush(request(type, PriorityClass.NORMAL));
        ServiceWorkflow workflow = ServiceWorkflow.create(request);
        workflow.addStage(type, null);
        workflows.saveAndFlush(workflow);

        assertThatThrownBy(() -> workflows.saveAndFlush(ServiceWorkflow.create(request)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stageSequenceIsUniqueWithinWorkflow() {
        ServiceType type = serviceTypes.saveAndFlush(type("Sequence check"));
        ServiceRequest request = requests.saveAndFlush(request(type, PriorityClass.NORMAL));
        ServiceWorkflow workflow = ServiceWorkflow.create(request);
        ServiceStage stage = workflow.addStage(type, null);
        workflows.saveAndFlush(workflow);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO service_stages (id, workflow_id, sequence_number, service_type_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), workflow.getId(),
                stage.getSequenceNumber(), type.getId(), "PENDING", Timestamp.from(Instant.now()), Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stageWorkflowForeignKeyIsEnforced() {
        ServiceType type = serviceTypes.saveAndFlush(type("Foreign key check"));
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO service_stages (id, workflow_id, sequence_number, service_type_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), UUID.randomUUID(), 3,
                type.getId(), "PENDING", Timestamp.from(Instant.now()), Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stageServiceTypeForeignKeyIsEnforced() {
        ServiceType type = serviceTypes.saveAndFlush(type("Service type foreign key"));
        ServiceRequest request = requests.saveAndFlush(request(type, PriorityClass.NORMAL));
        ServiceWorkflow workflow = ServiceWorkflow.create(request);
        workflows.saveAndFlush(workflow);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO service_stages (id, workflow_id, sequence_number, service_type_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), workflow.getId(), 1,
                UUID.randomUUID(), "PENDING", Timestamp.from(Instant.now()), Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void queueAndAssignmentUseAuthoritativeStageReferenceAndActiveStageIsUnique() {
        ServiceType type = serviceTypes.saveAndFlush(type("Repair"));
        Resource resource = resources.saveAndFlush(Resource.create("Counter A", Set.of(type)));
        ServiceRequest request = requests.saveAndFlush(request(type, PriorityClass.NORMAL));
        ServiceWorkflow workflow = ServiceWorkflow.create(request);
        ServiceStage stage = workflow.addStage(type, null);
        workflows.saveAndFlush(workflow);
        QueueEntry entry = queueEntries.saveAndFlush(QueueEntry.enter(stage, Instant.parse("2026-09-08T09:00:00Z")));
        Assignment assignment = assignments.saveAndFlush(Assignment.assign(
                stage, resource, Instant.parse("2026-09-08T09:01:00Z"), Duration.ofMinutes(20)));

        assertThat(entry.getServiceStage().getId()).isEqualTo(stage.getId());
        assertThat(assignment.getServiceStage().getId()).isEqualTo(stage.getId());
        assertThat(assignment.getServiceRequest().getId()).isEqualTo(request.getId());
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO assignments (id, service_request_id, service_stage_id, resource_id, assigned_at, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), request.getId(), stage.getId(), resource.getId(),
                Timestamp.from(Instant.parse("2026-09-08T09:02:00Z")), "ASSIGNED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyOneWaitingQueueEntryCanExistForAStage() {
        ServiceType type = serviceTypes.saveAndFlush(type("Queue uniqueness"));
        ServiceRequest request = requests.saveAndFlush(request(type, PriorityClass.NORMAL));
        ServiceWorkflow workflow = ServiceWorkflow.create(request);
        ServiceStage stage = workflow.addStage(type, null);
        workflows.saveAndFlush(workflow);
        queueEntries.saveAndFlush(QueueEntry.enter(stage, Instant.parse("2026-09-08T09:00:00Z")));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO queue_entries (id, service_request_id, service_stage_id, queued_at, status) "
                        + "VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), request.getId(), stage.getId(),
                Timestamp.from(Instant.parse("2026-09-08T09:02:00Z")), "WAITING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stageRepositoryFindsEligibleAndQueuedStages() {
        ServiceType type = serviceTypes.saveAndFlush(type("Quality check"));
        ServiceRequest request = requests.saveAndFlush(request(type, PriorityClass.NORMAL));
        ServiceWorkflow workflow = ServiceWorkflow.create(request);
        ServiceStage stage = workflow.addStage(type, null);
        workflows.saveAndFlush(workflow);

        assertThat(stages.findByStatusInOrderByEligibleAtAsc(
                List.of(ServiceStageStatus.ELIGIBLE, ServiceStageStatus.QUEUED)))
                .extracting(ServiceStage::getId).contains(stage.getId());
    }

    @Test
    void assignmentStartAndCompletionPersistLifecycleState() {
        ServiceType type = serviceTypes.saveAndFlush(type("Lifecycle persistence"));
        Resource resource = resources.saveAndFlush(Resource.create("Lifecycle Bay", Set.of(type)));
        ServiceRequest request = requests.saveAndFlush(request(type, PriorityClass.NORMAL));
        ServiceWorkflow workflow = ServiceWorkflow.create(request);
        ServiceStage stage = workflow.addStage(type, null);
        workflows.saveAndFlush(workflow);
        queueEntries.saveAndFlush(QueueEntry.enter(stage, Instant.now()));
        Assignment assignment = assignments.saveAndFlush(Assignment.assign(stage, resource, Instant.now(), null));

        assignmentApplicationService.start(assignment.getId());
        assignments.flush(); stages.flush(); resources.flush();
        Assignment started = assignments.findById(assignment.getId()).orElseThrow();
        assertThat(started.getStatus().name()).isEqualTo("IN_PROGRESS");
        assertThat(started.getServiceStage().getStatus()).isEqualTo(ServiceStageStatus.IN_PROGRESS);
        assertThat(started.getResource().getStatus()).isEqualTo(com.minor_project.optiserve_backend.operations.domain.ResourceStatus.BUSY);
        assertThat(started.getStartedAt()).isNotNull();

        assignmentApplicationService.complete(assignment.getId(), 12L);
        assignments.flush(); stages.flush(); resources.flush();
        Assignment completed = assignments.findById(assignment.getId()).orElseThrow();
        assertThat(completed.getStatus().name()).isEqualTo("COMPLETED");
        assertThat(completed.getServiceStage().getStatus()).isEqualTo(ServiceStageStatus.COMPLETED);
        assertThat(completed.getResource().getStatus()).isEqualTo(com.minor_project.optiserve_backend.operations.domain.ResourceStatus.AVAILABLE);
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(completed.getActualServiceDuration()).isEqualTo(Duration.ofMinutes(12));
    }

    private ServiceType type(String name) {
        return ServiceType.create(name, "Persistence test service", Duration.ofMinutes(15));
    }

    private ServiceRequest request(ServiceType serviceType, PriorityClass priorityClass) {
        Vehicle vehicle = vehicles.saveAndFlush(
                Vehicle.create(UUID.randomUUID(), "KA" + UUID.randomUUID().toString().substring(0, 8),
                        "Toyota", "Camry", 2024));
        return ServiceRequest.create(vehicle, serviceType, priorityClass, null);
    }
}
