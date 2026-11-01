package com.minor_project.optiserve_backend.operations.scheduler.application;

import com.minor_project.optiserve_backend.common.api.ResourceNotFoundException;
import com.minor_project.optiserve_backend.operations.scheduler.api.SchedulingCandidateEvaluationResponse;
import com.minor_project.optiserve_backend.operations.scheduler.api.SchedulingDecisionDetailResponse;
import com.minor_project.optiserve_backend.operations.scheduler.api.SchedulingDecisionPageResponse;
import com.minor_project.optiserve_backend.operations.scheduler.api.SchedulingDecisionSummaryResponse;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingDecisionResult;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingTrigger;
import com.minor_project.optiserve_backend.operations.scheduler.persistence.SchedulingCandidateEvaluation;
import com.minor_project.optiserve_backend.operations.scheduler.persistence.SchedulingCandidateEvaluationRepository;
import com.minor_project.optiserve_backend.operations.scheduler.persistence.SchedulingDecision;
import com.minor_project.optiserve_backend.operations.scheduler.persistence.SchedulingDecisionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulingDecisionQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SchedulingDecisionRepository decisionRepository;
    private final SchedulingCandidateEvaluationRepository candidateRepository;

    public SchedulingDecisionQueryService(
            SchedulingDecisionRepository decisionRepository,
            SchedulingCandidateEvaluationRepository candidateRepository) {
        this.decisionRepository = decisionRepository;
        this.candidateRepository = candidateRepository;
    }

    @Transactional(readOnly = true)
    public SchedulingDecisionPageResponse search(
            Instant from,
            Instant to,
            SchedulingTrigger trigger,
            SchedulingDecisionResult result,
            int page,
            int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        Pageable pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "evaluatedAt"));
        Specification<SchedulingDecision> filters = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("evaluatedAt"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("evaluatedAt"), to));
            }
            if (trigger != null) {
                predicates.add(criteriaBuilder.equal(root.get("trigger"), trigger));
            }
            if (result != null) {
                predicates.add(criteriaBuilder.equal(root.get("result"), result));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
        Page<SchedulingDecision> decisions = decisionRepository.findAll(filters, pageable);
        return new SchedulingDecisionPageResponse(
                decisions.getContent().stream().map(SchedulingDecisionQueryService::summary).toList(),
                decisions.getNumber(), safeSize, decisions.getTotalElements(), decisions.getTotalPages());
    }

    @Transactional(readOnly = true)
    public SchedulingDecisionDetailResponse findById(UUID id) {
        SchedulingDecision decision = decisionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduling decision was not found."));
        return new SchedulingDecisionDetailResponse(
                summary(decision),
                candidateRepository.findByDecisionIdOrderByEvaluationOrderAsc(id).stream()
                        .map(SchedulingDecisionQueryService::candidate)
                        .toList());
    }

    private static SchedulingDecisionSummaryResponse summary(SchedulingDecision decision) {
        return new SchedulingDecisionSummaryResponse(
                decision.getId(), decision.getEvaluatedAt(), decision.getTrigger(), decision.getStrategyName(),
                decision.getStrategyVersion(), decision.getResult(), decision.getReason(),
                decision.getSelectedStageId(), decision.getSelectedResourceId(), decision.getCandidateCount(),
                decision.getEvaluatedCandidateCount());
    }

    private static SchedulingCandidateEvaluationResponse candidate(SchedulingCandidateEvaluation evaluation) {
        return new SchedulingCandidateEvaluationResponse(
                evaluation.getQueueEntryId(), evaluation.getServiceStageId(), evaluation.getServiceRequestId(),
                evaluation.getWorkflowId(), evaluation.getEvaluationOrder(), evaluation.getQueueStatus(),
                evaluation.getStageStatus(), evaluation.getPriorityClass(), evaluation.getQueuedAt(),
                evaluation.getWaitingAgeMinutes(), evaluation.getAppointmentAt(), evaluation.isAppointmentDue(),
                evaluation.getPredictedServiceDuration(), evaluation.getPredictionSource(), evaluation.isCritical(),
                evaluation.getBasePriorityPoints(), evaluation.getAgingPoints(),
                evaluation.getAppointmentBonusPoints(), evaluation.getDurationBonusPoints(),
                evaluation.getFinalScore(), evaluation.getFeasibilityStatus(), evaluation.getObservedResourceStatus(),
                evaluation.getMatchedResourceId(), evaluation.getOutcome(), evaluation.getReason());
    }
}
