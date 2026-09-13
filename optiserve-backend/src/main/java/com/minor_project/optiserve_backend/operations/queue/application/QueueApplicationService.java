package com.minor_project.optiserve_backend.operations.queue.application;

import com.minor_project.optiserve_backend.common.api.ConflictException;
import com.minor_project.optiserve_backend.common.api.ResourceNotFoundException;
import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import com.minor_project.optiserve_backend.operations.persistence.QueueEntryRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceStageRepository;
import com.minor_project.optiserve_backend.operations.queue.api.QueueEntryResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueueApplicationService {

    private final QueueEntryRepository queueEntryRepository;
    private final ServiceStageRepository serviceStageRepository;

    public QueueApplicationService(
            QueueEntryRepository queueEntryRepository,
            ServiceStageRepository serviceStageRepository) {
        this.queueEntryRepository = queueEntryRepository;
        this.serviceStageRepository = serviceStageRepository;
    }

    @Transactional
    public QueueEntryResponse queueStage(UUID stageId) {
        ServiceStage stage = findStageForUpdate(stageId);
        if (stage.getStatus() != ServiceStageStatus.ELIGIBLE) {
            throw new ConflictException("Only an eligible service stage can enter the queue.");
        }
        if (queueEntryRepository.findByServiceStageIdAndStatus(stageId, QueueEntryStatus.WAITING).isPresent()) {
            throw new ConflictException("The service stage already has an active queue entry.");
        }

        QueueEntry queueEntry = QueueEntry.enter(stage, Instant.now());
        QueueEntry persistedEntry = queueEntryRepository.saveAndFlush(queueEntry);
        return toResponse(persistedEntry);
    }

    @Transactional(readOnly = true)
    public List<QueueEntryResponse> findQueuedStages() {
        return queueEntryRepository.findByStatusOrderByQueuedAtAsc(QueueEntryStatus.WAITING).stream()
                .map(QueueApplicationService::toResponse)
                .toList();
    }

    @Transactional
    public QueueEntryResponse removeStageFromQueue(UUID stageId) {
        ServiceStage stage = findStageForUpdate(stageId);
        if (stage.getStatus() != ServiceStageStatus.QUEUED) {
            throw new ConflictException("Only a queued service stage can be removed from the queue.");
        }
        QueueEntry queueEntry = queueEntryRepository.findByServiceStageIdAndStatus(stageId, QueueEntryStatus.WAITING)
                .orElseThrow(() -> new ConflictException("The queued service stage has no active queue entry."));

        queueEntry.remove();
        stage.returnToEligible(Instant.now());
        return toResponse(queueEntry);
    }

    private ServiceStage findStageForUpdate(UUID stageId) {
        return serviceStageRepository.findByIdForUpdate(stageId)
                .orElseThrow(() -> new ResourceNotFoundException("Service stage was not found."));
    }

    private static QueueEntryResponse toResponse(QueueEntry queueEntry) {
        ServiceStage stage = queueEntry.getServiceStage();
        return new QueueEntryResponse(
                queueEntry.getId(),
                stage.getId(),
                stage.getWorkflow().getId(),
                queueEntry.getServiceRequest().getId(),
                queueEntry.getServiceRequest().getVehicle().getId(),
                queueEntry.getServiceRequest().getVehicle().getRegistrationNumber(),
                stage.getServiceType().getId(),
                stage.getServiceType().getName(),
                queueEntry.getServiceRequest().getPriorityClass(),
                queueEntry.getQueuedAt(),
                queueEntry.getStatus(),
                stage.getStatus());
    }
}
