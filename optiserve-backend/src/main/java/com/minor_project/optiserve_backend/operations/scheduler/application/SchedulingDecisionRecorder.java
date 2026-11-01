package com.minor_project.optiserve_backend.operations.scheduler.application;

import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulerCandidateEvaluation;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingDecisionReason;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingDecisionResult;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingTrigger;
import com.minor_project.optiserve_backend.operations.scheduler.persistence.SchedulingCandidateEvaluation;
import com.minor_project.optiserve_backend.operations.scheduler.persistence.SchedulingDecision;
import com.minor_project.optiserve_backend.operations.scheduler.persistence.SchedulingCandidateEvaluationRepository;
import com.minor_project.optiserve_backend.operations.scheduler.persistence.SchedulingDecisionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Persists an immutable scheduling explanation in the caller's scheduling transaction. */
@Service
public class SchedulingDecisionRecorder {

    public static final String STRATEGY_NAME = "HYBRID";
    public static final String STRATEGY_VERSION = "1";

    private final SchedulingDecisionRepository decisionRepository;
    private final SchedulingCandidateEvaluationRepository candidateRepository;

    public SchedulingDecisionRecorder(
            SchedulingDecisionRepository decisionRepository,
            SchedulingCandidateEvaluationRepository candidateRepository) {
        this.decisionRepository = decisionRepository;
        this.candidateRepository = candidateRepository;
    }

    public SchedulingDecision record(
            Instant evaluatedAt,
            SchedulingTrigger trigger,
            SchedulingDecisionResult result,
            SchedulingDecisionReason reason,
            UUID selectedStageId,
            UUID selectedResourceId,
            List<SchedulerCandidateEvaluation> candidates,
            int evaluatedCandidateCount) {
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        Objects.requireNonNull(trigger, "trigger must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(candidates, "candidates must not be null");
        if (evaluatedCandidateCount < 0 || evaluatedCandidateCount > candidates.size()) {
            throw new IllegalArgumentException("evaluatedCandidateCount must be within candidate count");
        }
        SchedulingDecision decision = decisionRepository.saveAndFlush(new SchedulingDecision(
                evaluatedAt,
                trigger,
                STRATEGY_NAME,
                STRATEGY_VERSION,
                result,
                reason,
                selectedStageId,
                selectedResourceId,
                candidates.size(),
                evaluatedCandidateCount));
        candidateRepository.saveAllAndFlush(candidates.stream()
                .map(candidate -> new SchedulingCandidateEvaluation(decision, candidate))
                .toList());
        return decision;
    }
}
