CREATE TABLE scheduling_decisions (
    id UUID PRIMARY KEY,
    evaluated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    trigger_reason VARCHAR(48) NOT NULL,
    strategy_name VARCHAR(100) NOT NULL,
    strategy_version VARCHAR(32) NOT NULL,
    result VARCHAR(48) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    selected_stage_id UUID,
    selected_resource_id UUID,
    candidate_count INTEGER NOT NULL CHECK (candidate_count >= 0),
    evaluated_candidate_count INTEGER NOT NULL CHECK (evaluated_candidate_count >= 0)
);

ALTER TABLE scheduling_decisions
    ADD CONSTRAINT chk_scheduling_decision_trigger
        CHECK (trigger_reason IN ('MANUAL_ASSIGN_NEXT', 'AUTO_RECONCILIATION')),
    ADD CONSTRAINT chk_scheduling_decision_result
        CHECK (result IN ('ASSIGNED', 'NO_QUEUED_STAGE', 'NO_COMPATIBLE_RESOURCE'));

CREATE TABLE scheduling_candidate_evaluations (
    id UUID PRIMARY KEY,
    decision_id UUID NOT NULL REFERENCES scheduling_decisions(id),
    evaluation_order INTEGER NOT NULL CHECK (evaluation_order >= 0),
    queue_entry_id UUID,
    service_stage_id UUID,
    service_request_id UUID,
    workflow_id UUID,
    queue_status VARCHAR(32) NOT NULL,
    stage_status VARCHAR(32) NOT NULL,
    priority_class VARCHAR(32) NOT NULL,
    queued_at TIMESTAMP(6) WITH TIME ZONE,
    waiting_age_minutes BIGINT NOT NULL CHECK (waiting_age_minutes >= 0),
    appointment_at TIMESTAMP(6) WITH TIME ZONE,
    appointment_due BOOLEAN NOT NULL,
    predicted_service_duration NUMERIC(21, 0),
    prediction_source VARCHAR(32),
    critical BOOLEAN NOT NULL,
    base_priority_points BIGINT NOT NULL,
    aging_points BIGINT NOT NULL,
    appointment_bonus_points BIGINT NOT NULL,
    duration_bonus_points BIGINT NOT NULL,
    final_score BIGINT NOT NULL,
    feasibility_status VARCHAR(48) NOT NULL,
    observed_resource_status VARCHAR(32),
    matched_resource_id UUID,
    candidate_outcome VARCHAR(48) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    CONSTRAINT uq_scheduling_candidate_order UNIQUE (decision_id, evaluation_order)
);

ALTER TABLE scheduling_candidate_evaluations
    ADD CONSTRAINT chk_scheduling_candidate_outcome
        CHECK (candidate_outcome IN ('SELECTED', 'SKIPPED', 'NOT_EVALUATED')),
    ADD CONSTRAINT chk_scheduling_candidate_feasibility
        CHECK (feasibility_status IN ('NOT_EVALUATED', 'FEASIBLE', 'INFEASIBLE', 'STALE'));

CREATE INDEX idx_scheduling_decisions_evaluated_at
    ON scheduling_decisions (evaluated_at DESC);
CREATE INDEX idx_scheduling_decisions_trigger_time
    ON scheduling_decisions (trigger_reason, evaluated_at DESC);
CREATE INDEX idx_scheduling_decisions_result_time
    ON scheduling_decisions (result, evaluated_at DESC);
CREATE INDEX idx_scheduling_candidate_decision_order
    ON scheduling_candidate_evaluations (decision_id, evaluation_order);
