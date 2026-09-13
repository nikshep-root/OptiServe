package com.minor_project.optiserve_backend.operations.assignment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.when;

import com.minor_project.optiserve_backend.operations.domain.Assignment;
import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.ServiceWorkflow;
import com.minor_project.optiserve_backend.operations.domain.ServiceWorkflowStatus;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.operations.persistence.AssignmentRepository;
import com.minor_project.optiserve_backend.operations.persistence.QueueEntryRepository;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceStageRepository;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulerStrategy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignmentExecutionApplicationServiceTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-14T10:00:00Z"), ZoneOffset.UTC);
    @Mock private QueueEntryRepository queueEntries;
    @Mock private ServiceStageRepository stages;
    @Mock private ResourceRepository resources;
    @Mock private AssignmentRepository assignments;
    @Mock private SchedulerStrategy scheduler;

    @Test
    void startsAssignedAssignmentAndRejectsSecondStart() {
        Assignment assignment = assigned(); UUID id = UUID.randomUUID();
        when(assignments.findByIdForUpdate(id)).thenReturn(Optional.of(assignment));

        var response = service().start(id);

        assertThat(response.assignmentStatus().name()).isEqualTo("IN_PROGRESS");
        assertThat(response.stageStatus()).isEqualTo(ServiceStageStatus.IN_PROGRESS);
        assertThat(response.startedAt()).isEqualTo(Instant.now(CLOCK));
        assertThat(response.resourceStatus()).isEqualTo(ResourceStatus.BUSY);
        assertThatIllegalStateException().isThrownBy(() -> service().start(id));
    }

    @Test
    void rejectsCompletionBeforeStartAndCompletesWithExplicitDuration() {
        Assignment assignment = assigned(); UUID id = UUID.randomUUID();
        when(assignments.findByIdForUpdate(id)).thenReturn(Optional.of(assignment));
        assertThatIllegalStateException().isThrownBy(() -> service().complete(id, 12L));

        service().start(id);
        var response = service().complete(id, 12L);
        assertThat(response.assignmentStatus().name()).isEqualTo("COMPLETED");
        assertThat(response.stageStatus()).isEqualTo(ServiceStageStatus.COMPLETED);
        assertThat(response.completedAt()).isEqualTo(Instant.now(CLOCK));
        assertThat(response.actualServiceDuration()).isEqualTo(Duration.ofMinutes(12));
        assertThat(response.resourceStatus()).isEqualTo(ResourceStatus.AVAILABLE);
        assertThatIllegalStateException().isThrownBy(() -> service().complete(id, 12L));
    }

    @Test
    void completionWithoutDurationUsesElapsedTime() {
        Assignment assignment = assigned(); UUID id = UUID.randomUUID();
        when(assignments.findByIdForUpdate(id)).thenReturn(Optional.of(assignment));
        service().start(id);
        assertThat(service().complete(id, null).actualServiceDuration()).isZero();
    }

    @Test
    void completingStagesContinuesWorkflowWithoutAutomaticallyQueueingNextStage() {
        ServiceType type = ServiceType.create("Inspection", null, Duration.ofMinutes(15));
        ServiceRequest request = ServiceRequest.create(Vehicle.create(UUID.randomUUID(), "KA01AB1234", "Toyota", "Camry", 2024), type, PriorityClass.NORMAL, null);
        ServiceWorkflow workflow = ServiceWorkflow.create(request);
        var first = workflow.addStage(type, null); var second = workflow.addStage(type, null);
        QueueEntry.enter(first, Instant.now(CLOCK));
        Assignment firstAssignment = assign(first, type); firstAssignment.start(Instant.now(CLOCK)); firstAssignment.complete(Instant.now(CLOCK), Duration.ofMinutes(10));
        assertThat(first.getStatus()).isEqualTo(ServiceStageStatus.COMPLETED);
        assertThat(second.getStatus()).isEqualTo(ServiceStageStatus.ELIGIBLE);
        assertThat(workflow.getStatus()).isEqualTo(ServiceWorkflowStatus.ACTIVE);
        assertThat(request.getStatus().name()).isNotEqualTo("COMPLETED");
        QueueEntry.enter(second, Instant.now(CLOCK)); Assignment finalAssignment = assign(second, type);
        finalAssignment.start(Instant.now(CLOCK)); finalAssignment.complete(Instant.now(CLOCK), Duration.ofMinutes(10));
        assertThat(workflow.getStatus()).isEqualTo(ServiceWorkflowStatus.COMPLETED);
        assertThat(request.getStatus().name()).isEqualTo("COMPLETED");
    }

    @Test
    void offlineResourceRemainsOfflineWhenActiveAssignmentCompletes() {
        Assignment assignment = assigned(); assignment.start(Instant.now(CLOCK)); assignment.getResource().markOffline();
        assignment.complete(Instant.now(CLOCK), Duration.ofMinutes(5));
        assertThat(assignment.getResource().getStatus()).isEqualTo(ResourceStatus.OFFLINE);
        assertThat(assignment.getStatus().name()).isEqualTo("COMPLETED");
    }

    @Test
    void threeStageWorkflowProgressesOneEligibleStageAtATimeUntilFinalCompletion() {
        ServiceType type = ServiceType.create("Inspection", null, Duration.ofMinutes(15));
        ServiceRequest request = ServiceRequest.create(Vehicle.create(UUID.randomUUID(), "KA01AB1234", "Toyota", "Camry", 2024), type, PriorityClass.NORMAL, null);
        ServiceWorkflow workflow = ServiceWorkflow.create(request);
        var first = workflow.addStage(type, null); var second = workflow.addStage(type, null); var third = workflow.addStage(type, null);
        assertThat(first.getStatus()).isEqualTo(ServiceStageStatus.ELIGIBLE);
        assertThat(second.getStatus()).isEqualTo(ServiceStageStatus.PENDING);
        assertThat(third.getStatus()).isEqualTo(ServiceStageStatus.PENDING);
        completeStage(first, type);
        assertThat(second.getStatus()).isEqualTo(ServiceStageStatus.ELIGIBLE);
        assertThat(third.getStatus()).isEqualTo(ServiceStageStatus.PENDING);
        assertThat(workflow.getStatus()).isEqualTo(ServiceWorkflowStatus.ACTIVE);
        completeStage(second, type);
        assertThat(second.getStatus()).isEqualTo(ServiceStageStatus.COMPLETED);
        assertThat(third.getStatus()).isEqualTo(ServiceStageStatus.ELIGIBLE);
        assertThat(workflow.getStatus()).isEqualTo(ServiceWorkflowStatus.ACTIVE);
        assertThat(request.getStatus().name()).isNotEqualTo("COMPLETED");
        completeStage(third, type);
        assertThat(workflow.getStatus()).isEqualTo(ServiceWorkflowStatus.COMPLETED);
        assertThat(request.getStatus().name()).isEqualTo("COMPLETED");
    }

    private AssignmentApplicationService service() {
        return new AssignmentApplicationService(queueEntries, stages, resources, assignments, scheduler, CLOCK);
    }

    private Assignment assigned() {
        ServiceType type = ServiceType.create("Inspection", null, Duration.ofMinutes(15));
        ServiceRequest request = ServiceRequest.create(Vehicle.create(UUID.randomUUID(), "KA01AB1234", "Toyota", "Camry", 2024), type, PriorityClass.NORMAL, null);
        ServiceWorkflow workflow = ServiceWorkflow.create(request);
        var stage = workflow.addStage(type, null);
        QueueEntry.enter(stage, Instant.now(CLOCK));
        return Assignment.assign(stage, Resource.create("Bay", Set.of(type)), Instant.now(CLOCK), null);
    }

    private Assignment assign(com.minor_project.optiserve_backend.operations.domain.ServiceStage stage, ServiceType type) {
        return Assignment.assign(stage, Resource.create("Bay " + UUID.randomUUID(), Set.of(type)), Instant.now(CLOCK), null);
    }

    private void completeStage(com.minor_project.optiserve_backend.operations.domain.ServiceStage stage, ServiceType type) {
        QueueEntry.enter(stage, Instant.now(CLOCK));
        Assignment assignment = assign(stage, type);
        assignment.start(Instant.now(CLOCK));
        assignment.complete(Instant.now(CLOCK), Duration.ofMinutes(5));
    }
}
