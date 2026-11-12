-- Flarum server-to-server integration credentials.
INSERT INTO public.app_config_entries
    (config_key, config_value, value_type, config_group, source, sensitive, hot_reload)
VALUES
    ('sparkle.flarum-integration.token', '', 'string', 'sparkle.flarum-integration', 'sql-default', true, false)
ON CONFLICT (config_key) DO NOTHING;
