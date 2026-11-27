ALTER TABLE pbh_licenses
    ADD COLUMN slot_returned BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deleted_at TIMESTAMPTZ;

-- Preserve the slots already released by the previous donation-counting rule.
UPDATE pbh_licenses SET slot_returned = TRUE
WHERE source_type = 'DONATION' AND import_fingerprint IS NULL AND revoked_at IS NOT NULL;
