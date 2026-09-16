package com.minor_project.optiserve_backend.operations.queue.api;

import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import java.time.Instant;
import java.util.UUID;

public record WaitTimeResponse(
        UUID stageId,
        long estimatedWaitMinutes,
        QueueEntryStatus queueStatus,
        PriorityClass priority,
        Instant calculatedAt) {
}
