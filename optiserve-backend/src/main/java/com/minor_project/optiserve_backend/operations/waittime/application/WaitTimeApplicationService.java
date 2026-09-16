package com.minor_project.optiserve_backend.operations.waittime.application;

import com.minor_project.optiserve_backend.common.api.ConflictException;
import com.minor_project.optiserve_backend.common.api.ResourceNotFoundException;
import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import com.minor_project.optiserve_backend.operations.persistence.QueueEntryRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceStageRepository;
import com.minor_project.optiserve_backend.operations.queue.api.WaitTimeResponse;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaitTimeApplicationService {

    private final WaitTimeCalculationService waitTimeCalculationService;
    private final QueueEntryRepository queueEntryRepository;
    private final ServiceStageRepository serviceStageRepository;
    private final Clock clock;

    public WaitTimeApplicationService(
            WaitTimeCalculationService waitTimeCalculationService,
            QueueEntryRepository queueEntryRepository,
            ServiceStageRepository serviceStageRepository,
            Clock clock) {
        this.waitTimeCalculationService = waitTimeCalculationService;
        this.queueEntryRepository = queueEntryRepository;
        this.serviceStageRepository = serviceStageRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WaitTimeResponse estimateWaitTime(UUID stageId) {
        ServiceStage stage = serviceStageRepository.findById(stageId)
                .orElseThrow(() -> new ResourceNotFoundException("Service stage was not found."));
        QueueEntry queueEntry = queueEntryRepository.findByServiceStageIdAndStatus(stageId, QueueEntryStatus.WAITING)
                .orElseThrow(() -> new ConflictException("The service stage is not currently waiting in the queue."));
        if (stage.getStatus() != ServiceStageStatus.QUEUED) {
            throw new ConflictException("Only a queued service stage has a wait estimate.");
        }

        return new WaitTimeResponse(
                stageId,
                waitTimeCalculationService.estimateMinutes(stageId),
                queueEntry.getStatus(),
                queueEntry.getServiceRequest().getPriorityClass(),
                clock.instant());
    }
}
