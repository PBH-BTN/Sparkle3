-- Historical licenses can precede a Sparkle account binding.
ALTER TABLE public.pbh_licenses ALTER COLUMN btn_user_id DROP NOT NULL;
ALTER TABLE public.pbh_licenses
    ADD COLUMN import_fingerprint VARCHAR(64),
    ADD COLUMN import_message_id VARCHAR(128),
    ADD COLUMN import_match VARCHAR(16),
    ADD COLUMN imported_by BIGINT;
CREATE UNIQUE INDEX pbh_licenses_import_fingerprint_idx
    ON public.pbh_licenses (import_fingerprint) WHERE import_fingerprint IS NOT NULL;
CREATE INDEX pbh_licenses_external_account_idx
    ON public.pbh_licenses (provider, external_user_id);
-- Index compact hashes rather than potentially oversized license text.
CREATE INDEX pbh_licenses_key_hash_idx ON public.pbh_licenses (md5(license_key));
