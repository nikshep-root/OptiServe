package com.minor_project.optiserve_backend.operations.automation.application;

import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingTrigger;

public record WorkshopAutomationRequested(SchedulingTrigger trigger) {

    public WorkshopAutomationRequested {
        java.util.Objects.requireNonNull(trigger, "trigger must not be null");
    }

    public WorkshopAutomationRequested() {
        this(SchedulingTrigger.AUTO_RECONCILIATION);
    }
}
