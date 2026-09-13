package com.minor_project.optiserve_backend.operations.assignment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minor_project.optiserve_backend.operations.assignment.api.AssignmentNextResult;
import com.minor_project.optiserve_backend.operations.domain.Assignment;
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
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.operations.persistence.AssignmentRepository;
import com.minor_project.optiserve_backend.operations.persistence.QueueEntryRepository;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceStageRepository;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulerStrategy;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignmentApplicationServiceTests {

    @Mock private QueueEntryRepository queueEntryRepository;
    @Mock private ServiceStageRepository serviceStageRepository;
    @Mock private ResourceRepository resourceRepository;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private SchedulerStrategy schedulerStrategy;

    @Test
    void assignsSchedulerSelectedStageToCompatibleAvailableResourceAtomically() {
        QueueEntry queueEntry = queuedEntry();
        ServiceStage stage = queueEntry.getServiceStage();
        Resource resource = Resource.create("Inspection Bay", Set.of(stage.getServiceType()));
        when(queueEntryRepository.findByStatusOrderByQueuedAtAsc(QueueEntryStatus.WAITING)).thenReturn(List.of(queueEntry));
        when(schedulerStrategy.selectNext(List.of(queueEntry))).thenReturn(Optional.of(queueEntry));
        when(serviceStageRepository.findByIdForUpdate(stage.getId())).thenReturn(Optional.of(stage));
        when(queueEntryRepository.findByServiceStageIdAndStatus(stage.getId(), QueueEntryStatus.WAITING))
                .thenReturn(Optional.of(queueEntry));
        when(resourceRepository.findByStatusAndCompatibleServiceTypesIdForUpdate(ResourceStatus.AVAILABLE, stage.getServiceType().getId()))
                .thenReturn(List.of(resource));
        when(assignmentRepository.saveAndFlush(any(Assignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service().assignNext();

        assertThat(response.result()).isEqualTo(AssignmentNextResult.ASSIGNED);
        assertThat(stage.getStatus()).isEqualTo(ServiceStageStatus.ASSIGNED);
        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.BUSY);
        assertThat(queueEntry.getStatus()).isEqualTo(QueueEntryStatus.REMOVED);
        verify(assignmentRepository).saveAndFlush(any(Assignment.class));
    }

    @Test
    void returnsNoQueuedStageWithoutWritingWhenSchedulerHasNoCandidate() {
        when(queueEntryRepository.findByStatusOrderByQueuedAtAsc(QueueEntryStatus.WAITING)).thenReturn(List.of());
        when(schedulerStrategy.selectNext(List.of())).thenReturn(Optional.empty());

        assertThat(service().assignNext().result()).isEqualTo(AssignmentNextResult.NO_QUEUED_STAGE);
        verify(assignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void leavesQueueStateUntouchedWhenNoCompatibleAvailableResourceExists() {
        QueueEntry queueEntry = queuedEntry();
        ServiceStage stage = queueEntry.getServiceStage();
        when(queueEntryRepository.findByStatusOrderByQueuedAtAsc(QueueEntryStatus.WAITING)).thenReturn(List.of(queueEntry));
        when(schedulerStrategy.selectNext(List.of(queueEntry))).thenReturn(Optional.of(queueEntry));
        when(serviceStageRepository.findByIdForUpdate(stage.getId())).thenReturn(Optional.of(stage));
        when(queueEntryRepository.findByServiceStageIdAndStatus(stage.getId(), QueueEntryStatus.WAITING))
                .thenReturn(Optional.of(queueEntry));
        when(resourceRepository.findByStatusAndCompatibleServiceTypesIdForUpdate(ResourceStatus.AVAILABLE, stage.getServiceType().getId()))
                .thenReturn(List.of());

        assertThat(service().assignNext().result()).isEqualTo(AssignmentNextResult.NO_COMPATIBLE_RESOURCE);
        assertThat(stage.getStatus()).isEqualTo(ServiceStageStatus.QUEUED);
        assertThat(queueEntry.getStatus()).isEqualTo(QueueEntryStatus.WAITING);
        verify(assignmentRepository, never()).saveAndFlush(any());
    }

    private AssignmentApplicationService service() {
        return new AssignmentApplicationService(
                queueEntryRepository,
                serviceStageRepository,
                resourceRepository,
                assignmentRepository,
                schedulerStrategy,
                Clock.fixed(Instant.parse("2026-09-14T09:00:00Z"), ZoneOffset.UTC));
    }

    private QueueEntry queuedEntry() {
        ServiceType serviceType = ServiceType.create("Inspection", null, Duration.ofMinutes(15));
        setField(serviceType, "id", UUID.randomUUID());
        ServiceRequest request = ServiceRequest.create(
                Vehicle.create(UUID.randomUUID(), "KA01AB1234", "Toyota", "Camry", 2024),
                serviceType,
                PriorityClass.CRITICAL,
                null);
        ServiceWorkflow workflow = ServiceWorkflow.create(request);
        ServiceStage stage = workflow.addStage(serviceType, Duration.ofMinutes(15));
        setField(stage, "id", UUID.randomUUID());
        return QueueEntry.enter(stage, Instant.parse("2026-09-14T08:00:00Z"));
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
