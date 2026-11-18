CREATE TABLE btn_report_volume_cursors (
    module text PRIMARY KEY,
    stream_id text NOT NULL
);

INSERT INTO app_config_entries (config_key, config_value, value_type, config_group, source, hot_reload)
VALUES
    ('sparkle.ping.heartbeat.volume-risk-enabled', 'false', 'boolean', 'sparkle.ping', 'sql-default', true),
    ('sparkle.ping.heartbeat.volume-risk-threshold', '10', 'number', 'sparkle.ping', 'sql-default', true),
    ('sparkle.ping.heartbeat.volume-risk-recipient-uids', '', 'string', 'sparkle.ping', 'sql-default', true),
    ('sparkle.ping.sync-banhistory.volume-risk-enabled', 'false', 'boolean', 'sparkle.ping', 'sql-default', true),
    ('sparkle.ping.sync-banhistory.volume-risk-threshold', '10000', 'number', 'sparkle.ping', 'sql-default', true),
    ('sparkle.ping.sync-banhistory.volume-risk-recipient-uids', '', 'string', 'sparkle.ping', 'sql-default', true),
    ('sparkle.ping.sync-swarm.volume-risk-enabled', 'false', 'boolean', 'sparkle.ping', 'sql-default', true),
    ('sparkle.ping.sync-swarm.volume-risk-threshold', '100000', 'number', 'sparkle.ping', 'sql-default', true),
    ('sparkle.ping.sync-swarm.volume-risk-recipient-uids', '', 'string', 'sparkle.ping', 'sql-default', true)
ON CONFLICT (config_key) DO NOTHING;
