package com.minor_project.optiserve_backend.operations.scheduler.domain;

/** Identifies an actual scheduling invocation source for decision history. */
public enum SchedulingTrigger {
    MANUAL_ASSIGN_NEXT,
    AUTO_RECONCILIATION
}
