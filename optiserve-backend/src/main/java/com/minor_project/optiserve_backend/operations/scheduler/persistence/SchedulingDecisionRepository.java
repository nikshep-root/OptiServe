package com.minor_project.optiserve_backend.operations.scheduler.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SchedulingDecisionRepository
        extends JpaRepository<SchedulingDecision, UUID>, JpaSpecificationExecutor<SchedulingDecision> {
}
