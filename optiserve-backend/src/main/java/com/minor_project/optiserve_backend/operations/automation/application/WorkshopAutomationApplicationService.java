package com.minor_project.optiserve_backend.operations.automation.application;

import com.minor_project.optiserve_backend.operations.automation.api.WorkshopAutomationModeResponse;
import com.minor_project.optiserve_backend.operations.automation.domain.WorkshopAutomationMode;
import com.minor_project.optiserve_backend.operations.automation.domain.WorkshopAutomationSettings;
import com.minor_project.optiserve_backend.operations.automation.persistence.WorkshopAutomationSettingsRepository;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkshopAutomationApplicationService {

    private static final short SINGLETON_ID = 1;

    private final WorkshopAutomationSettingsRepository settingsRepository;
    private final ApplicationEventPublisher eventPublisher;

    public WorkshopAutomationApplicationService(
            WorkshopAutomationSettingsRepository settingsRepository,
            ApplicationEventPublisher eventPublisher) {
        this.settingsRepository = settingsRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public WorkshopAutomationModeResponse getMode() {
        WorkshopAutomationSettings settings = settingsRepository.findById(SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Workshop automation setting is missing"));
        return response(settings);
    }

    @Transactional
    public WorkshopAutomationModeResponse setMode(WorkshopAutomationMode mode) {
        WorkshopAutomationSettings settings = settingsRepository.findSingletonForUpdate(SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Workshop automation setting is missing"));
        settings.changeMode(mode, Instant.now());
        WorkshopAutomationModeResponse response = response(settings);
        if (mode == WorkshopAutomationMode.AUTO) {
            eventPublisher.publishEvent(new WorkshopAutomationRequested());
        }
        return response;
    }

    private static WorkshopAutomationModeResponse response(WorkshopAutomationSettings settings) {
        return new WorkshopAutomationModeResponse(settings.getMode(), settings.getUpdatedAt());
    }
}
