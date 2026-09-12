CREATE TABLE vehicles (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    registration_number VARCHAR(32) NOT NULL,
    make VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    year INTEGER NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_vehicles_registration_number UNIQUE (registration_number),
    CONSTRAINT chk_vehicles_year_positive CHECK (year > 0)
);
