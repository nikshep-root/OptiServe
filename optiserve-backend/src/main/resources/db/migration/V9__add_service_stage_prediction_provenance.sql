ALTER TABLE service_stages
    ADD COLUMN prediction_source VARCHAR(32) NOT NULL DEFAULT 'LEGACY_UNKNOWN';

ALTER TABLE service_stages
    ALTER COLUMN prediction_source DROP DEFAULT;

ALTER TABLE service_stages
    ADD CONSTRAINT chk_service_stages_prediction_source
        CHECK (prediction_source IN ('ML_MODEL', 'DEFAULT_FALLBACK', 'LEGACY_UNKNOWN'));
