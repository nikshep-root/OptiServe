package com.minor_project.optiserve_backend.operations.scheduler.domain;

import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import java.util.Collection;
import java.util.Optional;

/** Selects a queue candidate without changing queue, stage, resource, or assignment state. */
public interface SchedulerStrategy {

    Optional<QueueEntry> selectNext(Collection<QueueEntry> candidates);
}
