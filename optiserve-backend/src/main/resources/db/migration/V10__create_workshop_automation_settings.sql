CREATE TABLE workshop_automation_settings (
    id SMALLINT PRIMARY KEY,
    mode VARCHAR(16) NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_workshop_automation_settings_singleton CHECK (id = 1),
    CONSTRAINT chk_workshop_automation_settings_mode CHECK (mode IN ('MANUAL', 'AUTO'))
);

INSERT INTO workshop_automation_settings (id, mode, updated_at)
VALUES (1, 'MANUAL', CURRENT_TIMESTAMP);
