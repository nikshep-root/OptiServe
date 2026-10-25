package com.minor_project.optiserve_backend.operations.queue.api;

import com.minor_project.optiserve_backend.operations.queue.application.QueueApplicationService;
import com.minor_project.optiserve_backend.operations.automation.application.WorkshopAutomationRequested;
import com.minor_project.optiserve_backend.operations.waittime.application.WaitTimeApplicationService;
import com.minor_project.optiserve_backend.common.api.ResourceNotFoundException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueApplicationService queueApplicationService;
    private final WaitTimeApplicationService waitTimeApplicationService;
    private final ApplicationEventPublisher eventPublisher;

    public QueueController(
            QueueApplicationService queueApplicationService,
            WaitTimeApplicationService waitTimeApplicationService,
            ApplicationEventPublisher eventPublisher) {
        this.queueApplicationService = queueApplicationService;
        this.waitTimeApplicationService = waitTimeApplicationService;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/stages/{stageId}")
    public ResponseEntity<QueueEntryResponse> queueStage(@PathVariable UUID stageId, Authentication authentication) {
        requireOwnedStageOrAdmin(stageId, authentication);
        QueueEntryResponse response = queueApplicationService.queueStage(stageId);
        eventPublisher.publishEvent(new WorkshopAutomationRequested());
        return ResponseEntity.created(URI.create("/api/queue/stages/" + stageId)).body(response);
    }

    @GetMapping
    public List<QueueEntryResponse> findQueuedStages(Authentication authentication) {
        return isAdmin(authentication) ? queueApplicationService.findQueuedStages()
                : queueApplicationService.findQueuedStagesForCustomer(authentication.getName());
    }

    @GetMapping("/stages/{stageId}/wait-time")
    public WaitTimeResponse estimateWaitTime(@PathVariable UUID stageId, Authentication authentication) {
        requireOwnedStageOrAdmin(stageId, authentication);
        return waitTimeApplicationService.estimateWaitTime(stageId);
    }

    @DeleteMapping("/stages/{stageId}")
    public QueueEntryResponse removeStageFromQueue(@PathVariable UUID stageId, Authentication authentication) {
        requireOwnedStageOrAdmin(stageId, authentication);
        QueueEntryResponse response = queueApplicationService.removeStageFromQueue(stageId);
        eventPublisher.publishEvent(new WorkshopAutomationRequested());
        return response;
    }

    private void requireOwnedStageOrAdmin(UUID stageId, Authentication authentication) {
        if (!isAdmin(authentication) && !queueApplicationService.isStageOwnedByCustomer(stageId, authentication.getName())) {
            throw new ResourceNotFoundException("Service stage was not found.");
        }
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
