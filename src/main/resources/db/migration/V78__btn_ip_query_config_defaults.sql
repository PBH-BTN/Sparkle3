INSERT INTO public.app_config_entries
    (config_key, config_value, value_type, config_group, source, sensitive, hot_reload)
VALUES
    ('sparkle.ping.query-ip.lookup-endpoint', '{rooturl}/ping/queryIp/lookup', 'string', 'sparkle.ping', 'sql-default', false, true),
    ('sparkle.ping.query-ip.comments-endpoint', '{rooturl}/ping/queryIp/comments', 'string', 'sparkle.ping', 'sql-default', false, true),
    ('sparkle.ping.query-ip.comment-counts-endpoint', '{rooturl}/ping/queryIp/commentCounts', 'string', 'sparkle.ping', 'sql-default', false, true),
    ('sparkle.ping.query-ip.comment-count-pow-captcha', 'false', 'boolean', 'sparkle.ping', 'sql-default', false, true),
    ('sparkle.ping.query-ip.comment-count-requests-per-minute', '12', 'number', 'sparkle.ping', 'sql-default', false, true),
    ('sparkle.ping.query-ip.comment-count-cache-seconds', '300', 'number', 'sparkle.ping', 'sql-default', false, true)
ON CONFLICT (config_key) DO NOTHING;
