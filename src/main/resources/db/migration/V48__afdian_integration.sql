-- Afdian account linking and donation synchronization.

CREATE TABLE IF NOT EXISTS public.afdian_bindings (
    id BIGSERIAL PRIMARY KEY,
    btn_user_id BIGINT NOT NULL UNIQUE
        REFERENCES public."user" (id) ON DELETE CASCADE,
    afdian_user_id VARCHAR(128) NOT NULL UNIQUE,
    afdian_name VARCHAR(255),
    afdian_avatar VARCHAR(2048),
    bound_order_no VARCHAR(128) NOT NULL,
    bound_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_synced_at TIMESTAMPTZ,
    last_error TEXT
);

CREATE INDEX IF NOT EXISTS afdian_bindings_sync_idx
    ON public.afdian_bindings (last_synced_at NULLS FIRST, id);

CREATE TABLE IF NOT EXISTS public.afdian_donations (
    out_trade_no VARCHAR(128) PRIMARY KEY,
    afdian_user_id VARCHAR(128) NOT NULL,
    afdian_user_name VARCHAR(255),
    plan_title VARCHAR(255),
    total_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    show_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    order_status INTEGER NOT NULL DEFAULT 0,
    remark TEXT,
    created_at TIMESTAMPTZ,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    raw_data JSONB
);

CREATE INDEX IF NOT EXISTS afdian_donations_user_created_idx
    ON public.afdian_donations (afdian_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS afdian_donations_created_idx
    ON public.afdian_donations (created_at DESC);

INSERT INTO public.app_config_entries (
    config_key, config_value, value_type, config_group, source,
    sensitive, hot_reload
)
VALUES
    (
        'sparkle.afdian.api-base-url',
        'https://afdian.com/api/open',
        'string',
        'sparkle.afdian',
        'sql-default',
        FALSE,
        TRUE
    ),
    (
        'sparkle.afdian.user-id',
        '',
        'string',
        'sparkle.afdian',
        'sql-default',
        FALSE,
        TRUE
    ),
    (
        'sparkle.afdian.access-token',
        '',
        'string',
        'sparkle.afdian',
        'sql-default',
        TRUE,
        TRUE
    ),
    (
        'sparkle.afdian.sync-enabled',
        'true',
        'boolean',
        'sparkle.afdian',
        'sql-default',
        FALSE,
        TRUE
    )
ON CONFLICT (config_key) DO NOTHING;
