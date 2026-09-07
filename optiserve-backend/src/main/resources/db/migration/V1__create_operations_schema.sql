CREATE TABLE service_types (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(1000),
    default_service_duration NUMERIC(21, 0) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_service_types_name UNIQUE (name),
    CONSTRAINT chk_service_types_default_duration_positive CHECK (default_service_duration > 0)
);

CREATE TABLE resources (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_resources_name UNIQUE (name),
    CONSTRAINT chk_resources_status CHECK (status IN ('AVAILABLE', 'BUSY', 'OFFLINE'))
);

CREATE TABLE resource_service_type_capabilities (
    resource_id UUID NOT NULL,
    service_type_id UUID NOT NULL,
    PRIMARY KEY (resource_id, service_type_id),
    CONSTRAINT fk_resource_capabilities_resource
        FOREIGN KEY (resource_id) REFERENCES resources (id),
    CONSTRAINT fk_resource_capabilities_service_type
        FOREIGN KEY (service_type_id) REFERENCES service_types (id)
);

CREATE INDEX idx_resource_capabilities_service_type
    ON resource_service_type_capabilities (service_type_id, resource_id);

CREATE TABLE service_requests (
    id UUID PRIMARY KEY,
    service_type_id UUID NOT NULL,
    priority_class VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    appointment_at TIMESTAMP(6) WITH TIME ZONE,
    actual_service_duration NUMERIC(21, 0),
    CONSTRAINT fk_service_requests_service_type
        FOREIGN KEY (service_type_id) REFERENCES service_types (id),
    CONSTRAINT chk_service_requests_priority
        CHECK (priority_class IN ('CRITICAL', 'URGENT', 'APPOINTMENT', 'NORMAL')),
    CONSTRAINT chk_service_requests_status
        CHECK (status IN ('CREATED', 'WAITING', 'ASSIGNED', 'IN_SERVICE', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT chk_service_requests_appointment
        CHECK (priority_class <> 'APPOINTMENT' OR appointment_at IS NOT NULL),
    CONSTRAINT chk_service_requests_actual_duration
        CHECK (actual_service_duration IS NULL OR actual_service_duration >= 0)
);

CREATE INDEX idx_service_requests_waiting_priority_created
    ON service_requests (priority_class, requested_at)
    WHERE status = 'WAITING';

CREATE INDEX idx_service_requests_appointment_arrival
    ON service_requests (appointment_at)
    WHERE priority_class = 'APPOINTMENT' AND status = 'WAITING';

CREATE TABLE queue_entries (
    id UUID PRIMARY KEY,
    service_request_id UUID NOT NULL,
    queued_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT fk_queue_entries_service_request
        FOREIGN KEY (service_request_id) REFERENCES service_requests (id),
    CONSTRAINT chk_queue_entries_status CHECK (status IN ('WAITING', 'REMOVED'))
);

CREATE UNIQUE INDEX uq_queue_entries_waiting_request
    ON queue_entries (service_request_id)
    WHERE status = 'WAITING';

CREATE INDEX idx_queue_entries_waiting_queued_at
    ON queue_entries (queued_at)
    WHERE status = 'WAITING';

CREATE TABLE assignments (
    id UUID PRIMARY KEY,
    service_request_id UUID NOT NULL,
    resource_id UUID NOT NULL,
    assigned_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP(6) WITH TIME ZONE,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    predicted_service_duration NUMERIC(21, 0),
    actual_service_duration NUMERIC(21, 0),
    status VARCHAR(32) NOT NULL,
    CONSTRAINT fk_assignments_service_request
        FOREIGN KEY (service_request_id) REFERENCES service_requests (id),
    CONSTRAINT fk_assignments_resource
        FOREIGN KEY (resource_id) REFERENCES resources (id),
    CONSTRAINT chk_assignments_status
        CHECK (status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_assignments_predicted_duration
        CHECK (predicted_service_duration IS NULL OR predicted_service_duration > 0),
    CONSTRAINT chk_assignments_actual_duration
        CHECK (actual_service_duration IS NULL OR actual_service_duration >= 0),
    CONSTRAINT chk_assignments_started_after_assigned
        CHECK (started_at IS NULL OR started_at >= assigned_at),
    CONSTRAINT chk_assignments_completed_after_started
        CHECK (completed_at IS NULL OR (started_at IS NOT NULL AND completed_at >= started_at))
);

CREATE UNIQUE INDEX uq_assignments_active_request
    ON assignments (service_request_id)
    WHERE status IN ('ASSIGNED', 'IN_PROGRESS');

CREATE UNIQUE INDEX uq_assignments_active_resource
    ON assignments (resource_id)
    WHERE status IN ('ASSIGNED', 'IN_PROGRESS');

CREATE INDEX idx_assignments_resource_completed_at
    ON assignments (resource_id, completed_at);
