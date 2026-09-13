package com.minor_project.optiserve_backend.operations.queue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minor_project.optiserve_backend.common.api.ConflictException;
import com.minor_project.optiserve_backend.common.api.ResourceNotFoundException;
import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.ServiceWorkflow;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.operations.persistence.QueueEntryRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceStageRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueueApplicationServiceTests {

    @Mock private QueueEntryRepository queueEntryRepository;
    @Mock private ServiceStageRepository serviceStageRepository;

    @Test
    void queuesEligibleStageAtomically() {
        UUID stageId = UUID.randomUUID();
        ServiceStage stage = eligibleStage();
        when(serviceStageRepository.findByIdForUpdate(stageId)).thenReturn(Optional.of(stage));
        when(queueEntryRepository.findByServiceStageIdAndStatus(stageId, QueueEntryStatus.WAITING))
                .thenReturn(Optional.empty());
        when(queueEntryRepository.saveAndFlush(any(QueueEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().queueStage(stageId);

        assertThat(stage.getStatus()).isEqualTo(ServiceStageStatus.QUEUED);
        verify(queueEntryRepository).saveAndFlush(any(QueueEntry.class));
        verify(serviceStageRepository).findByIdForUpdate(stageId);
    }

    @Test
    void rejectsNonEligibleAndDuplicateQueueAttemptsWithoutCreatingAnotherEntry() {
        UUID stageId = UUID.randomUUID();
        ServiceStage pending = pendingStage();
        when(serviceStageRepository.findByIdForUpdate(stageId)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service().queueStage(stageId)).isInstanceOf(ConflictException.class);
        verify(queueEntryRepository, never()).saveAndFlush(any());

        ServiceStage queued = eligibleStage();
        QueueEntry.enter(queued, Instant.now());
        when(serviceStageRepository.findByIdForUpdate(stageId)).thenReturn(Optional.of(queued));
        assertThatThrownBy(() -> service().queueStage(stageId)).isInstanceOf(ConflictException.class);
        verify(queueEntryRepository, never()).saveAndFlush(any());
    }

    @Test
    void removesWaitingEntryAndReturnsStageToEligible() {
        UUID stageId = UUID.randomUUID();
        ServiceStage stage = eligibleStage();
        QueueEntry queueEntry = QueueEntry.enter(stage, Instant.now());
        when(serviceStageRepository.findByIdForUpdate(stageId)).thenReturn(Optional.of(stage));
        when(queueEntryRepository.findByServiceStageIdAndStatus(stageId, QueueEntryStatus.WAITING))
                .thenReturn(Optional.of(queueEntry));

        service().removeStageFromQueue(stageId);

        assertThat(queueEntry.getStatus()).isEqualTo(QueueEntryStatus.REMOVED);
        assertThat(stage.getStatus()).isEqualTo(ServiceStageStatus.ELIGIBLE);
    }

    @Test
    void rejectsUnknownStageWithoutWritingQueueData() {
        UUID stageId = UUID.randomUUID();
        when(serviceStageRepository.findByIdForUpdate(stageId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().queueStage(stageId)).isInstanceOf(ResourceNotFoundException.class);
        verify(queueEntryRepository, never()).saveAndFlush(any());
    }

    private QueueApplicationService service() {
        return new QueueApplicationService(queueEntryRepository, serviceStageRepository);
    }

    private ServiceStage eligibleStage() {
        ServiceType serviceType = ServiceType.create("Inspection", null, Duration.ofMinutes(15));
        ServiceRequest serviceRequest = ServiceRequest.create(
                Vehicle.create(UUID.randomUUID(), "KA01AB1234", "Toyota", "Camry", 2024),
                serviceType,
                PriorityClass.NORMAL,
                null);
        return ServiceWorkflow.create(serviceRequest).addStage(serviceType, null);
    }

    private ServiceStage pendingStage() {
        ServiceType serviceType = ServiceType.create("Inspection", null, Duration.ofMinutes(15));
        ServiceRequest serviceRequest = ServiceRequest.create(
                Vehicle.create(UUID.randomUUID(), "KA01AB1234", "Toyota", "Camry", 2024),
                serviceType,
                PriorityClass.NORMAL,
                null);
        ServiceWorkflow workflow = ServiceWorkflow.create(serviceRequest);
        workflow.addStage(serviceType, null);
        return workflow.addStage(serviceType, null);
    }
}
