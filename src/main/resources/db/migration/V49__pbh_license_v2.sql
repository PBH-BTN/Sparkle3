-- PBHLicense v2 issuance backed by Afdian product purchases and donations.

ALTER TABLE public.afdian_donations
    ADD COLUMN IF NOT EXISTS plan_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS license_consumed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS license_consumed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS afdian_donations_license_consumed_idx
    ON public.afdian_donations (afdian_user_id, plan_id, created_at)
    WHERE order_status = 2 AND license_consumed = FALSE;

CREATE TABLE IF NOT EXISTS public.pbh_licenses (
    id BIGSERIAL PRIMARY KEY,
    license_id UUID NOT NULL UNIQUE,
    btn_user_id BIGINT NOT NULL
        REFERENCES public."user" (id) ON DELETE CASCADE,
    afdian_user_id VARCHAR(128) NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    source_order_no VARCHAR(128),
    plan_id VARCHAR(255),
    consumed_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    license_key TEXT NOT NULL,
    license_payload JSONB NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    start_at TIMESTAMPTZ NOT NULL,
    expire_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_reason TEXT,
    CONSTRAINT pbh_licenses_source_type_check
        CHECK (source_type IN ('PRODUCT', 'DONATION'))
);

CREATE INDEX IF NOT EXISTS pbh_licenses_user_issued_idx
    ON public.pbh_licenses (btn_user_id, issued_at DESC);

INSERT INTO public.app_config_entries (
    config_key, config_value, value_type, config_group, source,
    sensitive, hot_reload
)
VALUES
    (
        'sparkle.afdian.license-plan-ids',
        '',
        'string',
        'sparkle.afdian',
        'sql-default',
        FALSE,
        TRUE
    ),
    (
        'sparkle.afdian.license-donation-amount',
        '50',
        'number',
        'sparkle.afdian',
        'sql-default',
        FALSE,
        TRUE
    ),
    (
        'sparkle.afdian.license-valid-years',
        '100',
        'number',
        'sparkle.afdian',
        'sql-default',
        FALSE,
        TRUE
    ),
    (
        'sparkle.afdian.license-private-key',
        '',
        'string',
        'sparkle.afdian',
        'sql-default',
        TRUE,
        TRUE
    )
ON CONFLICT (config_key) DO NOTHING;
