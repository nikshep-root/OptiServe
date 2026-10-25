package com.minor_project.optiserve_backend.operations.automation.application;

import com.minor_project.optiserve_backend.operations.assignment.api.AssignmentNextResult;
import com.minor_project.optiserve_backend.operations.assignment.application.AssignmentApplicationService;
import com.minor_project.optiserve_backend.operations.automation.domain.WorkshopAutomationMode;
import com.minor_project.optiserve_backend.operations.automation.domain.WorkshopAutomationSettings;
import com.minor_project.optiserve_backend.operations.automation.persistence.WorkshopAutomationSettingsRepository;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import com.minor_project.optiserve_backend.operations.persistence.QueueEntryRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceStageRepository;
import com.minor_project.optiserve_backend.operations.queue.application.QueueApplicationService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkshopAutomationCoordinator {

    private static final short SINGLETON_ID = 1;

    private final WorkshopAutomationSettingsRepository settingsRepository;
    private final ServiceStageRepository stageRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final QueueApplicationService queueApplicationService;
    private final AssignmentApplicationService assignmentApplicationService;

    public WorkshopAutomationCoordinator(
            WorkshopAutomationSettingsRepository settingsRepository,
            ServiceStageRepository stageRepository,
            QueueEntryRepository queueEntryRepository,
            QueueApplicationService queueApplicationService,
            AssignmentApplicationService assignmentApplicationService) {
        this.settingsRepository = settingsRepository;
        this.stageRepository = stageRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.queueApplicationService = queueApplicationService;
        this.assignmentApplicationService = assignmentApplicationService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcile() {
        WorkshopAutomationSettings settings = settingsRepository.findSingletonForUpdate(SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Workshop automation setting is missing"));
        if (settings.getMode() != WorkshopAutomationMode.AUTO) {
            return;
        }

        stageRepository.findByStatusInOrderByEligibleAtAsc(List.of(ServiceStageStatus.ELIGIBLE))
                .forEach(stage -> queueApplicationService.queueStage(stage.getId()));

        int assignmentAttempts = queueEntryRepository.findByStatusOrderByQueuedAtAsc(
                com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus.WAITING).size();
        for (int attempt = 0; attempt < assignmentAttempts; attempt++) {
            if (assignmentApplicationService.assignNext().result() != AssignmentNextResult.ASSIGNED) {
                break;
            }
        }
    }
}
