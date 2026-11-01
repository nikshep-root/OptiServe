package com.minor_project.optiserve_backend.operations.assignment.application;

import com.minor_project.optiserve_backend.operations.assignment.api.AssignmentNextResponse;
import com.minor_project.optiserve_backend.operations.assignment.api.AssignmentNextResult;
import com.minor_project.optiserve_backend.operations.assignment.api.AssignmentSummaryResponse;
import com.minor_project.optiserve_backend.operations.automation.application.WorkshopAutomationRequested;
import com.minor_project.optiserve_backend.operations.domain.Assignment;
import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import com.minor_project.optiserve_backend.operations.persistence.AssignmentRepository;
import com.minor_project.optiserve_backend.operations.persistence.QueueEntryRepository;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceStageRepository;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulerStrategy;
import com.minor_project.optiserve_backend.operations.scheduler.application.SchedulingDecisionRecorder;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulerCandidateEvaluation;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingCandidateOutcome;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingCandidateReason;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingDecisionReason;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingDecisionResult;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingFeasibilityStatus;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingTrigger;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentApplicationService {

    private final QueueEntryRepository queueEntryRepository;
    private final ServiceStageRepository serviceStageRepository;
    private final ResourceRepository resourceRepository;
    private final AssignmentRepository assignmentRepository;
    private final SchedulerStrategy schedulerStrategy;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final SchedulingDecisionRecorder decisionRecorder;

    @Autowired
    public AssignmentApplicationService(
            QueueEntryRepository queueEntryRepository,
            ServiceStageRepository serviceStageRepository,
            ResourceRepository resourceRepository,
            AssignmentRepository assignmentRepository,
            SchedulerStrategy schedulerStrategy,
            Clock clock,
            ApplicationEventPublisher eventPublisher,
            SchedulingDecisionRecorder decisionRecorder) {
        this.queueEntryRepository = queueEntryRepository;
        this.serviceStageRepository = serviceStageRepository;
        this.resourceRepository = resourceRepository;
        this.assignmentRepository = assignmentRepository;
        this.schedulerStrategy = schedulerStrategy;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
        this.decisionRecorder = decisionRecorder;
    }

    /** Compatibility constructor retained for focused unit tests that do not persist traces. */
    public AssignmentApplicationService(
            QueueEntryRepository queueEntryRepository,
            ServiceStageRepository serviceStageRepository,
            ResourceRepository resourceRepository,
            AssignmentRepository assignmentRepository,
            SchedulerStrategy schedulerStrategy,
            Clock clock,
            ApplicationEventPublisher eventPublisher) {
        this(queueEntryRepository, serviceStageRepository, resourceRepository, assignmentRepository,
                schedulerStrategy, clock, eventPublisher, null);
    }

    @Transactional
    public AssignmentNextResponse assignNext() {
        return assignNext(SchedulingTrigger.MANUAL_ASSIGN_NEXT);
    }

    @Transactional
    public AssignmentNextResponse assignNext(SchedulingTrigger trigger) {
        Instant evaluatedAt = Instant.now(clock);
        List<QueueEntry> waitingEntries = queueEntryRepository.findByStatusOrderByQueuedAtAsc(QueueEntryStatus.WAITING);
        List<SchedulerCandidateEvaluation> rankedCandidates = schedulerStrategy.evaluateCandidates(waitingEntries);
        if (rankedCandidates == null || (rankedCandidates.isEmpty() && !waitingEntries.isEmpty())) {
            List<QueueEntry> legacyRankedCandidates = schedulerStrategy.rankCandidates(waitingEntries);
            if (legacyRankedCandidates == null) {
                legacyRankedCandidates = List.of();
            }
            List<QueueEntry> legacyCandidates = legacyRankedCandidates;
            rankedCandidates = java.util.stream.IntStream.range(0, legacyCandidates.size())
                    .mapToObj(index -> SchedulerCandidateEvaluation.legacy(legacyCandidates.get(index), index))
                    .toList();
        }
        if (rankedCandidates.isEmpty()) {
            recordDecision(evaluatedAt, trigger, SchedulingDecisionResult.NO_QUEUED_STAGE,
                    SchedulingDecisionReason.NO_QUEUED_STAGE, null, null, List.of(), 0);
            return noAssignment(AssignmentNextResult.NO_QUEUED_STAGE);
        }

        List<SchedulerCandidateEvaluation> snapshots = new ArrayList<>();
        boolean foundCurrentQueuedCandidate = false;
        int evaluatedCandidateCount = 0;
        for (int index = 0; index < rankedCandidates.size(); index++) {
            SchedulerCandidateEvaluation candidateSnapshot = rankedCandidates.get(index);
            QueueEntry candidate = candidateSnapshot.queueEntry();
            ServiceStage stage = serviceStageRepository.findByIdForUpdate(candidate.getServiceStage().getId())
                    .orElse(null);
            if (stage == null || stage.getStatus() != ServiceStageStatus.QUEUED) {
                evaluatedCandidateCount++;
                snapshots.add(candidateSnapshot.withDecision(
                        SchedulingFeasibilityStatus.STALE, null, null,
                        SchedulingCandidateOutcome.SKIPPED,
                        SchedulingCandidateReason.STAGE_NO_LONGER_ELIGIBLE));
                continue;
            }
            java.util.Optional<QueueEntry> lockedQueueEntry = queueEntryRepository
                    .findByServiceStageIdAndStatusForUpdate(stage.getId(), QueueEntryStatus.WAITING);
            // A null result is only possible from legacy mocks; production repositories always return Optional.
            if (lockedQueueEntry == null || lockedQueueEntry.isEmpty()) {
                java.util.Optional<QueueEntry> legacyQueueEntry = queueEntryRepository
                        .findByServiceStageIdAndStatus(stage.getId(), QueueEntryStatus.WAITING);
                if (legacyQueueEntry != null && legacyQueueEntry.isPresent()) {
                    lockedQueueEntry = legacyQueueEntry;
                }
            }
            QueueEntry queueEntry = lockedQueueEntry.orElse(null);
            if (queueEntry == null) {
                evaluatedCandidateCount++;
                snapshots.add(candidateSnapshot.withDecision(
                        SchedulingFeasibilityStatus.STALE, null, null,
                        SchedulingCandidateOutcome.SKIPPED,
                        SchedulingCandidateReason.QUEUE_ENTRY_NO_LONGER_WAITING));
                continue;
            }
            foundCurrentQueuedCandidate = true;
            evaluatedCandidateCount++;

            List<Resource> resources = resourceRepository.findByStatusAndCompatibleServiceTypesIdForUpdate(
                    ResourceStatus.AVAILABLE, stage.getServiceType().getId());
            if (resources.isEmpty()) {
                snapshots.add(candidateSnapshot.withDecision(
                        SchedulingFeasibilityStatus.INFEASIBLE, null, null,
                        SchedulingCandidateOutcome.SKIPPED,
                        SchedulingCandidateReason.RESOURCE_UNAVAILABLE));
                continue;
            }

            Resource resource = resources.getFirst();
            Assignment assignment = Assignment.assign(stage, resource, Instant.now(clock), stage.getPredictedServiceDuration());
            queueEntry.remove();
            Assignment persistedAssignment = assignmentRepository.saveAndFlush(assignment);
            snapshots.add(candidateSnapshot.withDecision(
                    SchedulingFeasibilityStatus.FEASIBLE, ResourceStatus.AVAILABLE, resource.getId(),
                    SchedulingCandidateOutcome.SELECTED, SchedulingCandidateReason.SELECTED));
            for (int lowerIndex = index + 1; lowerIndex < rankedCandidates.size(); lowerIndex++) {
                snapshots.add(rankedCandidates.get(lowerIndex).withDecision(
                        SchedulingFeasibilityStatus.NOT_EVALUATED, null, null,
                        SchedulingCandidateOutcome.NOT_EVALUATED,
                        SchedulingCandidateReason.LOWER_RANKED_CANDIDATE));
            }
            recordDecision(evaluatedAt, trigger, SchedulingDecisionResult.ASSIGNED,
                    SchedulingDecisionReason.ASSIGNED, stage.getId(), resource.getId(), snapshots,
                    evaluatedCandidateCount);
            return assigned(persistedAssignment);
        }

        AssignmentNextResult result = foundCurrentQueuedCandidate
                ? AssignmentNextResult.NO_COMPATIBLE_RESOURCE : AssignmentNextResult.NO_QUEUED_STAGE;
        recordDecision(evaluatedAt, trigger,
                result == AssignmentNextResult.NO_COMPATIBLE_RESOURCE
                        ? SchedulingDecisionResult.NO_COMPATIBLE_RESOURCE
                        : SchedulingDecisionResult.NO_QUEUED_STAGE,
                result == AssignmentNextResult.NO_COMPATIBLE_RESOURCE
                        ? SchedulingDecisionReason.NO_COMPATIBLE_RESOURCE
                        : SchedulingDecisionReason.NO_QUEUED_STAGE,
                null, null, snapshots, evaluatedCandidateCount);
        return noAssignment(result);
    }

    private void recordDecision(
            Instant evaluatedAt,
            SchedulingTrigger trigger,
            SchedulingDecisionResult result,
            SchedulingDecisionReason reason,
            UUID selectedStageId,
            UUID selectedResourceId,
            List<SchedulerCandidateEvaluation> candidates,
            int evaluatedCandidateCount) {
        if (decisionRecorder != null) {
            decisionRecorder.record(evaluatedAt, trigger, result, reason, selectedStageId, selectedResourceId,
                    candidates, evaluatedCandidateCount);
        }
    }

    @Transactional(readOnly = true)
    public List<AssignmentSummaryResponse> findAllAssignments() {
        return assignmentRepository.findAllByOrderByAssignedAtDesc().stream()
                .map(AssignmentApplicationService::assignmentSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentSummaryResponse> findAssignmentsForCustomer(String email) {
        return assignmentRepository
                .findByServiceRequest_Vehicle_Customer_User_EmailIgnoreCaseOrderByAssignedAtDesc(email)
                .stream()
                .map(AssignmentApplicationService::assignmentSummary)
                .toList();
    }

    @Transactional
    public com.minor_project.optiserve_backend.operations.assignment.api.AssignmentExecutionResponse start(UUID assignmentId) {
        Assignment assignment = findAssignmentForUpdate(assignmentId);
        assignment.start(Instant.now(clock));
        return executionResponse(assignment);
    }

    @Transactional
    public com.minor_project.optiserve_backend.operations.assignment.api.AssignmentExecutionResponse complete(UUID assignmentId, Long actualDurationMinutes) {
        Assignment assignment = findAssignmentForUpdate(assignmentId);
        resourceRepository.findByIdForUpdate(assignment.getResource().getId())
                .orElseThrow(() -> new com.minor_project.optiserve_backend.common.api.ResourceNotFoundException(
                        "Resource was not found."));
        Duration duration = actualDurationMinutes == null ? null : Duration.ofMinutes(actualDurationMinutes);
        assignment.complete(Instant.now(clock), duration);
        eventPublisher.publishEvent(new WorkshopAutomationRequested());
        return executionResponse(assignment);
    }

    private Assignment findAssignmentForUpdate(java.util.UUID assignmentId) {
        return assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new com.minor_project.optiserve_backend.common.api.ResourceNotFoundException("Assignment was not found."));
    }

    private static com.minor_project.optiserve_backend.operations.assignment.api.AssignmentExecutionResponse executionResponse(Assignment assignment) {
        return new com.minor_project.optiserve_backend.operations.assignment.api.AssignmentExecutionResponse(
                assignment.getId(), assignment.getStatus(), assignment.getServiceStage().getStatus(),
                assignment.getResource().getStatus(), assignment.getStartedAt(), assignment.getCompletedAt(), assignment.getActualServiceDuration());
    }

    private static AssignmentNextResponse noAssignment(AssignmentNextResult result) {
        return new AssignmentNextResponse(result, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static AssignmentNextResponse assigned(Assignment assignment) {
        ServiceStage stage = assignment.getServiceStage();
        return new AssignmentNextResponse(
                AssignmentNextResult.ASSIGNED,
                assignment.getId(),
                assignment.getServiceRequest().getId(),
                assignment.getServiceRequest().getVehicle().getId(),
                assignment.getServiceRequest().getVehicle().getRegistrationNumber(),
                stage.getWorkflow().getId(),
                stage.getId(),
                stage.getServiceType().getId(),
                stage.getServiceType().getName(),
                assignment.getServiceRequest().getPriorityClass(),
                assignment.getResource().getId(),
                assignment.getResource().getName(),
                assignment.getStatus());
    }

    private static AssignmentSummaryResponse assignmentSummary(Assignment assignment) {
        ServiceStage stage = assignment.getServiceStage();
        return new AssignmentSummaryResponse(
                assignment.getId(),
                assignment.getServiceRequest().getId(),
                stage.getId(),
                assignment.getServiceRequest().getVehicle().getRegistrationNumber(),
                stage.getServiceType().getName(),
                assignment.getServiceRequest().getPriorityClass(),
                assignment.getResource().getName(),
                assignment.getStatus(),
                assignment.getAssignedAt(),
                assignment.getStartedAt(),
                assignment.getCompletedAt(),
                assignment.getPredictedServiceDuration(),
                assignment.getActualServiceDuration(),
                stage.getPredictionSource());
    }
}
