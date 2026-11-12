-- Configurable settlement currency with cached real-time exchange rates.

CREATE TABLE IF NOT EXISTS public.currency_exchange_rates (
    base_currency VARCHAR(8) NOT NULL,
    quote_currency VARCHAR(8) NOT NULL,
    rate_to_base NUMERIC(24, 12) NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    source VARCHAR(2048),
    PRIMARY KEY (base_currency, quote_currency)
);

ALTER TABLE public.member_transactions
    ADD COLUMN IF NOT EXISTS amount_base NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS base_currency VARCHAR(8);

UPDATE public.member_transactions
SET amount_base = COALESCE(amount_base, amount_cny),
    base_currency = COALESCE(base_currency, 'CNY');

INSERT INTO public.app_config_entries (
    config_key, config_value, value_type, config_group, source,
    sensitive, hot_reload
)
VALUES
    (
        'sparkle.memberships.base-currency',
        'CNY',
        'string',
        'sparkle.memberships',
        'sql-default',
        FALSE,
        TRUE
    ),
    (
        'sparkle.memberships.exchange-rate-api-url',
        'https://open.er-api.com/v6/latest/{currency}',
        'string',
        'sparkle.memberships',
        'sql-default',
        FALSE,
        TRUE
    )
ON CONFLICT (config_key) DO NOTHING;
