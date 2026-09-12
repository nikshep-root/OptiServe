CREATE TABLE service_workflows (
    id UUID PRIMARY KEY,
    service_request_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_service_workflows_service_request UNIQUE (service_request_id),
    CONSTRAINT fk_service_workflows_service_request
        FOREIGN KEY (service_request_id) REFERENCES service_requests (id),
    CONSTRAINT chk_service_workflows_status
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE service_stages (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL,
    sequence_number INTEGER NOT NULL,
    service_type_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    eligible_at TIMESTAMP(6) WITH TIME ZONE,
    predicted_service_duration NUMERIC(21, 0),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_service_stages_workflow_sequence UNIQUE (workflow_id, sequence_number),
    CONSTRAINT fk_service_stages_workflow
        FOREIGN KEY (workflow_id) REFERENCES service_workflows (id),
    CONSTRAINT fk_service_stages_service_type
        FOREIGN KEY (service_type_id) REFERENCES service_types (id),
    CONSTRAINT chk_service_stages_sequence_positive CHECK (sequence_number > 0),
    CONSTRAINT chk_service_stages_status
        CHECK (status IN ('PENDING', 'ELIGIBLE', 'QUEUED', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_service_stages_predicted_duration
        CHECK (predicted_service_duration IS NULL OR predicted_service_duration > 0)
);

CREATE INDEX idx_service_stages_schedulable
    ON service_stages (status, service_type_id, eligible_at);

ALTER TABLE queue_entries ADD COLUMN service_stage_id UUID;
ALTER TABLE queue_entries
    ADD CONSTRAINT fk_queue_entries_service_stage
    FOREIGN KEY (service_stage_id) REFERENCES service_stages (id);
CREATE UNIQUE INDEX uq_queue_entries_waiting_stage
    ON queue_entries (service_stage_id)
    WHERE status = 'WAITING' AND service_stage_id IS NOT NULL;
CREATE INDEX idx_queue_entries_stage ON queue_entries (service_stage_id);

ALTER TABLE assignments ADD COLUMN service_stage_id UUID;
ALTER TABLE assignments
    ADD CONSTRAINT fk_assignments_service_stage
    FOREIGN KEY (service_stage_id) REFERENCES service_stages (id);
CREATE UNIQUE INDEX uq_assignments_active_stage
    ON assignments (service_stage_id)
    WHERE status IN ('ASSIGNED', 'IN_PROGRESS') AND service_stage_id IS NOT NULL;
CREATE INDEX idx_assignments_stage ON assignments (service_stage_id);
