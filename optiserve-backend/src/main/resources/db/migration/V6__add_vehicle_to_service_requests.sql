ALTER TABLE service_requests
    ADD COLUMN vehicle_id UUID NOT NULL;

ALTER TABLE service_requests
    ADD CONSTRAINT fk_service_requests_vehicle
    FOREIGN KEY (vehicle_id) REFERENCES vehicles (id);

CREATE INDEX idx_service_requests_vehicle_id ON service_requests (vehicle_id);
