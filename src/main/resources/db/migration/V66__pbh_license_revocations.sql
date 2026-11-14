CREATE TABLE pbh_license_revocations (
    fingerprint VARCHAR(64) PRIMARY KEY,
    field_hashes JSONB NOT NULL,
    reason VARCHAR(500) NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_by BIGINT NOT NULL
);
