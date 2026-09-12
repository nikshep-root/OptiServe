CREATE UNIQUE INDEX uq_resources_name_case_insensitive
    ON resources (LOWER(name));
