package com.minor_project.optiserve_backend.operations.automation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "workshop_automation_settings")
public class WorkshopAutomationSettings {

    @Id
    private Short id;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 16)
    private WorkshopAutomationMode mode;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkshopAutomationSettings() {
    }

    public void changeMode(WorkshopAutomationMode mode, Instant updatedAt) {
        this.mode = java.util.Objects.requireNonNull(mode, "mode must not be null");
        this.updatedAt = java.util.Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public WorkshopAutomationMode getMode() {
        return mode;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
