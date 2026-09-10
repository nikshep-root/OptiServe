CREATE UNIQUE INDEX uq_service_types_name_case_insensitive
    ON service_types (LOWER(name));
