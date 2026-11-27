-- Fast defaults give existing rows an upgrade grace period without a table-wide backfill.
ALTER TABLE public.user_career_events
    ADD COLUMN last_seen timestamptz NOT NULL DEFAULT now();
ALTER TABLE public.user_lifetime_traffic_checkpoint
    ADD COLUMN last_seen timestamptz NOT NULL DEFAULT now();

INSERT INTO public.app_config_entries
    (config_key, config_value, value_type, config_group, source, sensitive, hot_reload)
VALUES ('sparkle.career.dedup-retention-days', '7', 'number', 'sparkle.career', 'sql-default', FALSE, TRUE)
ON CONFLICT (config_key) DO NOTHING;
