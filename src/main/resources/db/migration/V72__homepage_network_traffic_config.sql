INSERT INTO public.app_config_entries
    (config_key, config_value, value_type, config_group, source, sensitive, hot_reload)
VALUES
    ('sparkle.homepage.network-traffic-enabled', 'true', 'boolean', 'sparkle.homepage', 'sql-default', false, true)
ON CONFLICT (config_key) DO NOTHING;
