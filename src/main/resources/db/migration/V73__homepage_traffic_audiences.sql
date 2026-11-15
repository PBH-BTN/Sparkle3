INSERT INTO public.app_config_entries
    (config_key, config_value, value_type, config_group, source, sensitive, hot_reload)
VALUES
    ('sparkle.homepage.network-traffic-staff-only', 'false', 'boolean', 'sparkle.homepage', 'sql-default', false, true),
    ('sparkle.homepage.total-loss-staff-only', 'true', 'boolean', 'sparkle.homepage', 'sql-default', false, true)
ON CONFLICT (config_key) DO NOTHING;
