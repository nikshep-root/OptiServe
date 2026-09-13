package com.minor_project.optiserve_backend.operations.queue.api;

import com.minor_project.optiserve_backend.operations.queue.application.QueueApplicationService;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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

    public QueueController(QueueApplicationService queueApplicationService) {
        this.queueApplicationService = queueApplicationService;
    }

    @PostMapping("/stages/{stageId}")
    public ResponseEntity<QueueEntryResponse> queueStage(@PathVariable UUID stageId) {
        QueueEntryResponse response = queueApplicationService.queueStage(stageId);
        return ResponseEntity.created(URI.create("/api/queue/stages/" + stageId)).body(response);
    }

    @GetMapping
    public List<QueueEntryResponse> findQueuedStages() {
        return queueApplicationService.findQueuedStages();
    }

    @DeleteMapping("/stages/{stageId}")
    public QueueEntryResponse removeStageFromQueue(@PathVariable UUID stageId) {
        return queueApplicationService.removeStageFromQueue(stageId);
    }
}
