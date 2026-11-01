package com.minor_project.optiserve_backend.operations.scheduler.domain;

public enum SchedulingCandidateReason {
    SELECTED,
    RESOURCE_UNAVAILABLE,
    NO_COMPATIBLE_RESOURCE,
    STAGE_NO_LONGER_ELIGIBLE,
    QUEUE_ENTRY_NO_LONGER_WAITING,
    LOWER_RANKED_CANDIDATE,
    OTHER_VALIDATED_REASON
}
