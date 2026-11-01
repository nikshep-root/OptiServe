package com.minor_project.optiserve_backend.operations.scheduler.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulingCandidateEvaluationRepository
        extends JpaRepository<SchedulingCandidateEvaluation, UUID> {

    List<SchedulingCandidateEvaluation> findByDecisionIdOrderByEvaluationOrderAsc(UUID decisionId);
}
