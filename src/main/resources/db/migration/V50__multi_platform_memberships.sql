-- Provider-neutral membership accounts and normalized product/donation transactions.

CREATE TABLE IF NOT EXISTS public.member_platform_configs (
    provider VARCHAR(32) PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    api_url VARCHAR(2048),
    account_id VARCHAR(255),
    access_token VARCHAR(4096),
    webhook_token VARCHAR(4096),
    currency VARCHAR(16) NOT NULL DEFAULT 'CNY',
    exchange_rate NUMERIC(18, 8) NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT
);

INSERT INTO public.member_platform_configs (provider)
VALUES ('PATREON'), ('KOFI'), ('MIANBAODUO'), ('LIANDONG')
ON CONFLICT (provider) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.member_platform_bindings (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    btn_user_id BIGINT NOT NULL
        REFERENCES public."user" (id) ON DELETE CASCADE,
    external_user_id VARCHAR(255) NOT NULL,
    external_user_name VARCHAR(255),
    external_user_avatar VARCHAR(2048),
    proof VARCHAR(255) NOT NULL,
    bound_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_synced_at TIMESTAMPTZ,
    last_error TEXT,
    CONSTRAINT member_platform_bindings_user_unique
        UNIQUE (provider, btn_user_id),
    CONSTRAINT member_platform_bindings_external_unique
        UNIQUE (provider, external_user_id)
);

CREATE INDEX IF NOT EXISTS member_platform_bindings_user_idx
    ON public.member_platform_bindings (btn_user_id);

CREATE TABLE IF NOT EXISTS public.member_transactions (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    external_transaction_id VARCHAR(255) NOT NULL,
    external_user_id VARCHAR(255) NOT NULL,
    external_user_name VARCHAR(255),
    transaction_type VARCHAR(16) NOT NULL,
    sku VARCHAR(255),
    product_name VARCHAR(255),
    amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    currency VARCHAR(16) NOT NULL DEFAULT 'CNY',
    amount_cny NUMERIC(18, 2) NOT NULL DEFAULT 0,
    transaction_status VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ,
    raw_data JSONB,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    license_consumed BOOLEAN NOT NULL DEFAULT FALSE,
    license_consumed_at TIMESTAMPTZ,
    CONSTRAINT member_transactions_external_unique
        UNIQUE (provider, external_transaction_id),
    CONSTRAINT member_transactions_type_check
        CHECK (transaction_type IN ('PRODUCT', 'DONATION'))
);

CREATE INDEX IF NOT EXISTS member_transactions_user_time_idx
    ON public.member_transactions (provider, external_user_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS member_transactions_slots_idx
    ON public.member_transactions (transaction_type, transaction_status, occurred_at)
    WHERE license_consumed = FALSE;

ALTER TABLE public.pbh_licenses
    ADD COLUMN IF NOT EXISTS provider VARCHAR(32) NOT NULL DEFAULT 'AFDIAN',
    ADD COLUMN IF NOT EXISTS external_user_id VARCHAR(255);

ALTER TABLE public.pbh_licenses
    ALTER COLUMN afdian_user_id DROP NOT NULL;

UPDATE public.pbh_licenses
SET provider = 'AFDIAN',
    external_user_id = COALESCE(external_user_id, afdian_user_id)
WHERE external_user_id IS NULL;

INSERT INTO public.member_platform_bindings (
    provider, btn_user_id, external_user_id, external_user_name,
    external_user_avatar, proof, bound_at, last_synced_at, last_error
)
SELECT 'AFDIAN', btn_user_id, afdian_user_id, afdian_name,
       afdian_avatar, bound_order_no, bound_at, last_synced_at, last_error
FROM public.afdian_bindings
ON CONFLICT DO NOTHING;

INSERT INTO public.member_transactions (
    provider, external_transaction_id, external_user_id, external_user_name,
    transaction_type, sku, product_name, amount, currency, amount_cny,
    transaction_status, occurred_at, raw_data, synced_at,
    license_consumed, license_consumed_at
)
SELECT
    'AFDIAN',
    out_trade_no,
    afdian_user_id,
    afdian_user_name,
    'DONATION',
    plan_id,
    plan_title,
    total_amount,
    'CNY',
    show_amount,
    order_status::text,
    created_at,
    raw_data,
    synced_at,
    license_consumed,
    license_consumed_at
FROM public.afdian_donations
ON CONFLICT DO NOTHING;
