package com.minor_project.optiserve_backend.operations.automation.api;

import com.minor_project.optiserve_backend.operations.automation.domain.WorkshopAutomationMode;
import java.time.Instant;

public record WorkshopAutomationModeResponse(WorkshopAutomationMode mode, Instant updatedAt) {
}
