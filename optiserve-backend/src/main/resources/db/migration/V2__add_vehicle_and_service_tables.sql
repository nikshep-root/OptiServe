-- V2: Replace generic scheduling resources with explicit customers,
-- vehicles, mechanics and service bays, and connect service requests
-- and assignments to the new domain model.
--
-- Assumption: V1 has the exact schema supplied in the conversation and
-- there is no production data that must be preserved from `resources`.

CREATE TABLE customers (
                           id UUID PRIMARY KEY,
                           name VARCHAR(150) NOT NULL,
                           email VARCHAR(255),
                           password VARCHAR(255),
                           phone VARCHAR(32),
                           address VARCHAR(500),
                           role VARCHAR(32) NOT NULL DEFAULT 'ROLE_CUSTOMER',
                           created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                           updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                           CONSTRAINT uq_customers_email UNIQUE (email),
                           CONSTRAINT uq_customers_phone UNIQUE (phone)
);

CREATE TABLE vehicles (
                          id UUID PRIMARY KEY,
                          customer_id UUID,
                          registration_number VARCHAR(32) NOT NULL,
                          make VARCHAR(100) NOT NULL,
                          model VARCHAR(100) NOT NULL,
                          manufacturing_year INTEGER,
                          fuel_type VARCHAR(32),
                          color VARCHAR(64),
                          vin VARCHAR(64),
                          current_mileage BIGINT,
                          created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                          updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                          CONSTRAINT fk_vehicles_customer
                              FOREIGN KEY (customer_id) REFERENCES customers (id),
                          CONSTRAINT uq_vehicles_registration_number UNIQUE (registration_number),
                          CONSTRAINT uq_vehicles_vin UNIQUE (vin),
                          CONSTRAINT chk_vehicles_manufacturing_year
                              CHECK (manufacturing_year IS NULL OR manufacturing_year BETWEEN 1886 AND 2100),
                          CONSTRAINT chk_vehicles_current_mileage
                              CHECK (current_mileage IS NULL OR current_mileage >= 0)
);

CREATE INDEX idx_vehicles_customer_id
    ON vehicles (customer_id);

CREATE TABLE mechanics (
                           id UUID PRIMARY KEY,
                           employee_id VARCHAR(64) NOT NULL,
                           name VARCHAR(150) NOT NULL,
                           phone VARCHAR(32),
                           status VARCHAR(32) NOT NULL,
                           joined_at DATE,
                           created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                           updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                           CONSTRAINT uq_mechanics_employee_id UNIQUE (employee_id),
                           CONSTRAINT chk_mechanics_status
                               CHECK (status IN ('AVAILABLE', 'BUSY', 'ON_LEAVE', 'OFFLINE'))
);

CREATE TABLE service_bays (
                              id UUID PRIMARY KEY,
                              bay_number VARCHAR(32) NOT NULL,
                              bay_type VARCHAR(32) NOT NULL,
                              status VARCHAR(32) NOT NULL,
                              created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                              updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                              CONSTRAINT uq_service_bays_bay_number UNIQUE (bay_number),
                              CONSTRAINT chk_service_bays_type
                                  CHECK (bay_type IN ('GENERAL', 'DIAGNOSTIC', 'ALIGNMENT', 'BODY_REPAIR', 'EV')),
                              CONSTRAINT chk_service_bays_status
                                  CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'MAINTENANCE', 'OFFLINE'))
);

-- Capabilities are kept separately so the scheduler can find a mechanic
-- qualified for a service and a bay capable of supporting that service.
CREATE TABLE mechanic_service_type_capabilities (
                                                    mechanic_id UUID NOT NULL,
                                                    service_type_id UUID NOT NULL,
                                                    PRIMARY KEY (mechanic_id, service_type_id),
                                                    CONSTRAINT fk_mechanic_capabilities_mechanic
                                                        FOREIGN KEY (mechanic_id) REFERENCES mechanics (id),
                                                    CONSTRAINT fk_mechanic_capabilities_service_type
                                                        FOREIGN KEY (service_type_id) REFERENCES service_types (id)
);

CREATE INDEX idx_mechanic_capabilities_service_type
    ON mechanic_service_type_capabilities (service_type_id, mechanic_id);

CREATE TABLE bay_service_type_capabilities (
                                               service_bay_id UUID NOT NULL,
                                               service_type_id UUID NOT NULL,
                                               PRIMARY KEY (service_bay_id, service_type_id),
                                               CONSTRAINT fk_bay_capabilities_bay
                                                   FOREIGN KEY (service_bay_id) REFERENCES service_bays (id),
                                               CONSTRAINT fk_bay_capabilities_service_type
                                                   FOREIGN KEY (service_type_id) REFERENCES service_types (id)
);

CREATE INDEX idx_bay_capabilities_service_type
    ON bay_service_type_capabilities (service_type_id, service_bay_id);

-- service_requests now belongs to a vehicle. The customer is obtained through
-- vehicles.customer_id, which avoids storing the same relationship twice.
ALTER TABLE service_requests
    ADD COLUMN vehicle_id UUID;

ALTER TABLE service_requests
    ADD CONSTRAINT fk_service_requests_vehicle
        FOREIGN KEY (vehicle_id) REFERENCES vehicles (id);

CREATE INDEX idx_service_requests_vehicle_id
    ON service_requests (vehicle_id);

-- Existing V1 rows can be backfilled before this is made NOT NULL.
-- Keep it nullable for the migration; make it NOT NULL after backfilling.

-- Remove the V1 resource-based assignment model.
DROP INDEX IF EXISTS uq_assignments_active_resource;
DROP INDEX IF EXISTS idx_assignments_resource_completed_at;

ALTER TABLE assignments
DROP CONSTRAINT IF EXISTS fk_assignments_resource;

ALTER TABLE assignments
DROP COLUMN IF EXISTS resource_id;

ALTER TABLE assignments
    ADD COLUMN mechanic_id UUID;

ALTER TABLE assignments
    ADD COLUMN bay_id UUID;

ALTER TABLE assignments
    ADD CONSTRAINT fk_assignments_mechanic
        FOREIGN KEY (mechanic_id) REFERENCES mechanics (id);

ALTER TABLE assignments
    ADD CONSTRAINT fk_assignments_bay
        FOREIGN KEY (bay_id) REFERENCES service_bays (id);

CREATE INDEX idx_assignments_mechanic_id
    ON assignments (mechanic_id, completed_at);

CREATE INDEX idx_assignments_bay_id
    ON assignments (bay_id, completed_at);

CREATE UNIQUE INDEX uq_assignments_active_mechanic
    ON assignments (mechanic_id)
    WHERE status IN ('ASSIGNED', 'IN_PROGRESS');

CREATE UNIQUE INDEX uq_assignments_active_bay
    ON assignments (bay_id)
    WHERE status IN ('ASSIGNED', 'IN_PROGRESS');

-- Drop the old V1 resource capability model after assignments no longer depend
-- on it. These tables are no longer part of the domain model.
DROP TABLE IF EXISTS resource_service_type_capabilities;
DROP TABLE IF EXISTS resources;
