package com.minor_project.optiserve_backend.operations.scheduler.persistence;

import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingDecisionReason;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingDecisionResult;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingTrigger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scheduling_decisions")
public class SchedulingDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private Instant evaluatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", nullable = false, length = 48, updatable = false)
    private SchedulingTrigger trigger;

    @Column(name = "strategy_name", nullable = false, length = 100, updatable = false)
    private String strategyName;

    @Column(name = "strategy_version", nullable = false, length = 32, updatable = false)
    private String strategyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 48, updatable = false)
    private SchedulingDecisionResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 64, updatable = false)
    private SchedulingDecisionReason reason;

    @Column(name = "selected_stage_id", updatable = false)
    private UUID selectedStageId;

    @Column(name = "selected_resource_id", updatable = false)
    private UUID selectedResourceId;

    @Column(name = "candidate_count", nullable = false, updatable = false)
    private int candidateCount;

    @Column(name = "evaluated_candidate_count", nullable = false, updatable = false)
    private int evaluatedCandidateCount;

    protected SchedulingDecision() {
    }

    public SchedulingDecision(
            Instant evaluatedAt,
            SchedulingTrigger trigger,
            String strategyName,
            String strategyVersion,
            SchedulingDecisionResult result,
            SchedulingDecisionReason reason,
            UUID selectedStageId,
            UUID selectedResourceId,
            int candidateCount,
            int evaluatedCandidateCount) {
        this.evaluatedAt = evaluatedAt;
        this.trigger = trigger;
        this.strategyName = strategyName;
        this.strategyVersion = strategyVersion;
        this.result = result;
        this.reason = reason;
        this.selectedStageId = selectedStageId;
        this.selectedResourceId = selectedResourceId;
        this.candidateCount = candidateCount;
        this.evaluatedCandidateCount = evaluatedCandidateCount;
    }

    public UUID getId() { return id; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
    public SchedulingTrigger getTrigger() { return trigger; }
    public String getStrategyName() { return strategyName; }
    public String getStrategyVersion() { return strategyVersion; }
    public SchedulingDecisionResult getResult() { return result; }
    public SchedulingDecisionReason getReason() { return reason; }
    public UUID getSelectedStageId() { return selectedStageId; }
    public UUID getSelectedResourceId() { return selectedResourceId; }
    public int getCandidateCount() { return candidateCount; }
    public int getEvaluatedCandidateCount() { return evaluatedCandidateCount; }
}
