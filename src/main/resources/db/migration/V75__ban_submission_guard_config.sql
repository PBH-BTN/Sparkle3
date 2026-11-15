INSERT INTO public.app_config_entries
    (config_key, config_value, value_type, config_group, source, sensitive, hot_reload)
VALUES
    ('sparkle.ping.sync-banhistory.repeat-ban-suppression', 'false', 'boolean', 'sparkle.ping', 'sql-default', false, true),
    ('sparkle.ping.sync-banhistory.repeat-ban-windows', '5', 'number', 'sparkle.ping', 'sql-default', false, true)
ON CONFLICT (config_key) DO NOTHING;
