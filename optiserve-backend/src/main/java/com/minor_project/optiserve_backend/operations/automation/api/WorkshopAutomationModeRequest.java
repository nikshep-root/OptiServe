package com.minor_project.optiserve_backend.operations.automation.api;

import com.minor_project.optiserve_backend.operations.automation.domain.WorkshopAutomationMode;
import jakarta.validation.constraints.NotNull;

public record WorkshopAutomationModeRequest(@NotNull WorkshopAutomationMode mode) {
}
