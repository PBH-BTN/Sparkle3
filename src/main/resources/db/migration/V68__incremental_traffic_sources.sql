-- No history backfill: expensive data loading belongs to resumable monitored tasks.
CREATE TABLE public.dashboard_traffic_incremental_state (
    task TEXT PRIMARY KEY,
    watermark TIMESTAMPTZ NOT NULL,
    backfill_cursor TIMESTAMPTZ,
    initialized BOOLEAN NOT NULL DEFAULT FALSE
);
INSERT INTO public.dashboard_traffic_incremental_state(task, watermark, backfill_cursor) VALUES
    ('shared-swarm', NOW(), date_trunc('hour', NOW()) - INTERVAL '30 days'),
    ('shared-ban', NOW(), date_trunc('hour', NOW()) - INTERVAL '30 days');

CREATE TABLE public.dashboard_traffic_swarm_checkpoint (
    purpose TEXT NOT NULL,
    id BIGINT NOT NULL,
    city TEXT NOT NULL,
    peer_id TEXT NOT NULL,
    client_name TEXT NOT NULL,
    ip_version INTEGER NOT NULL,
    upload_bytes BIGINT NOT NULL,
    download_bytes BIGINT NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (purpose, id)
);
CREATE INDEX dashboard_traffic_swarm_checkpoint_time_idx
    ON public.dashboard_traffic_swarm_checkpoint(purpose, last_seen_at);

CREATE TABLE public.dashboard_traffic_swarm_hourly (
    purpose TEXT NOT NULL,
    bucket TIMESTAMPTZ NOT NULL,
    dimension TEXT NOT NULL,
    name TEXT NOT NULL,
    upload_bytes NUMERIC NOT NULL,
    download_bytes NUMERIC NOT NULL,
    PRIMARY KEY (purpose, bucket, dimension, name)
);
CREATE INDEX dashboard_traffic_swarm_hourly_time_idx ON public.dashboard_traffic_swarm_hourly(bucket);

CREATE TABLE public.dashboard_traffic_counts (
    source TEXT NOT NULL,
    bucket TIMESTAMPTZ NOT NULL,
    dimension TEXT NOT NULL,
    name TEXT NOT NULL,
    value BIGINT NOT NULL,
    PRIMARY KEY (source, bucket, dimension, name)
);
CREATE INDEX dashboard_traffic_counts_time_idx ON public.dashboard_traffic_counts(bucket);
-- Deduplication is needed only around the committed ingestion watermark, not for 30 days.
CREATE TABLE public.dashboard_traffic_ban_event_checkpoint (
    id BIGINT NOT NULL,
    populate_time TIMESTAMPTZ NOT NULL,
    insert_time TIMESTAMPTZ NOT NULL,
    peer_id TEXT NOT NULL,
    client_name TEXT NOT NULL,
    city TEXT NOT NULL,
    ip_version INTEGER NOT NULL,
    PRIMARY KEY (id, populate_time)
);
CREATE INDEX dashboard_traffic_ban_event_checkpoint_time_idx
    ON public.dashboard_traffic_ban_event_checkpoint(insert_time);

CREATE TABLE public.dashboard_traffic_ban_ranking (
    bucket TIMESTAMPTZ NOT NULL,
    userapps_id BIGINT NOT NULL,
    torrent_id BIGINT NOT NULL,
    peer_ip INET NOT NULL,
    peer_port INTEGER NOT NULL,
    peer_id TEXT NOT NULL,
    client_name TEXT NOT NULL,
    city TEXT NOT NULL,
    upload_bytes BIGINT NOT NULL,
    download_bytes BIGINT NOT NULL,
    PRIMARY KEY (bucket, userapps_id, torrent_id, peer_ip, peer_port, peer_id)
);

-- New schedules inherit the old switch, so a disabled installation stays disabled.
INSERT INTO public.app_config_entries
    (config_key, config_value, value_type, config_group, sensitive, hot_reload, source)
SELECT 'sparkle.homepage-statistics-refresh.modules.' || module || '.' || setting,
       CASE WHEN setting = 'enabled' THEN COALESCE(
           (SELECT config_value FROM public.app_config_entries
            WHERE config_key = 'sparkle.homepage-statistics-refresh.modules.bittorrent-traffic.enabled'), 'true')
           ELSE minutes END,
       CASE WHEN setting = 'enabled' THEN 'boolean' ELSE 'number' END,
       'sparkle.homepage-statistics-refresh', FALSE, TRUE, 'default'
FROM (VALUES ('bittorrent-traffic-data', '60'), ('bittorrent-traffic-rankings', '720')) modules(module, minutes)
CROSS JOIN (VALUES ('enabled'), ('refresh-interval-minutes')) settings(setting)
ON CONFLICT (config_key) DO NOTHING;

-- Swarm counts describe current tracker rows. Apply retention/account deletions
-- in one statement, without scanning the tracker or firing a trigger per row.
CREATE TABLE public.dashboard_traffic_swarm_changes (
    id BIGINT PRIMARY KEY,
    changed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX dashboard_traffic_swarm_changes_time_idx ON public.dashboard_traffic_swarm_changes(changed_at);
CREATE FUNCTION public.mark_dashboard_swarm_changes() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO public.dashboard_traffic_swarm_changes AS changes(id, changed_at)
    SELECT id, clock_timestamp() FROM changed_swarm_rows ORDER BY id
    ON CONFLICT (id) DO UPDATE SET changed_at = EXCLUDED.changed_at;
    RETURN NULL;
END;
$$;
CREATE TRIGGER dashboard_swarm_insert_changes AFTER INSERT ON public.swarm_tracker
REFERENCING NEW TABLE AS changed_swarm_rows
FOR EACH STATEMENT EXECUTE FUNCTION public.mark_dashboard_swarm_changes();
CREATE TRIGGER dashboard_swarm_update_changes AFTER UPDATE ON public.swarm_tracker
REFERENCING NEW TABLE AS changed_swarm_rows
FOR EACH STATEMENT EXECUTE FUNCTION public.mark_dashboard_swarm_changes();

CREATE FUNCTION public.remove_dashboard_swarm_contributions() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    DELETE FROM public.dashboard_traffic_swarm_changes changes
    USING deleted_swarm_rows removed WHERE changes.id = removed.id;
    WITH removed AS (
        DELETE FROM public.dashboard_traffic_swarm_checkpoint checkpoint
        USING deleted_swarm_rows removed
        WHERE checkpoint.purpose = 'shared' AND checkpoint.id = removed.id
        RETURNING checkpoint.*
    ), deltas AS (
        SELECT last_seen_at, d.* FROM removed
        CROSS JOIN LATERAL (VALUES
            ('city', city, -upload_bytes::numeric, -download_bytes::numeric),
            ('peer', peer_id, -upload_bytes::numeric, -download_bytes::numeric),
            ('client', client_name, -upload_bytes::numeric, -download_bytes::numeric),
            ('geo' || ip_version, city, 0::numeric, 0::numeric)
        ) d(dimension, name, upload, download)
    ), flow AS (
        INSERT INTO public.dashboard_traffic_swarm_hourly AS hourly
            (purpose, bucket, dimension, name, upload_bytes, download_bytes)
        SELECT 'shared', date_trunc('hour', last_seen_at, 'UTC'), dimension, name,
               SUM(upload), SUM(download)
        FROM deltas WHERE dimension IN ('city', 'peer', 'client')
          AND last_seen_at >= date_trunc('hour', NOW(), 'UTC') + INTERVAL '1 hour' - INTERVAL '7 days'
        GROUP BY date_trunc('hour', last_seen_at, 'UTC'), dimension, name
        ON CONFLICT (purpose, bucket, dimension, name) DO UPDATE
        SET upload_bytes = hourly.upload_bytes + EXCLUDED.upload_bytes,
            download_bytes = hourly.download_bytes + EXCLUDED.download_bytes
        RETURNING 1
    )
    INSERT INTO public.dashboard_traffic_counts AS counts(source, bucket, dimension, name, value)
    SELECT 'swarm', date_trunc('hour', last_seen_at, 'UTC'), dimension, name, -COUNT(*)
    FROM deltas WHERE dimension != 'city'
      AND last_seen_at >= date_trunc('day', NOW(), 'UTC') + INTERVAL '1 day' - INTERVAL '30 days'
    GROUP BY date_trunc('hour', last_seen_at, 'UTC'), dimension, name
    ON CONFLICT (source, bucket, dimension, name) DO UPDATE SET value = counts.value + EXCLUDED.value;
    RETURN NULL;
END;
$$;
CREATE TRIGGER dashboard_swarm_remove_contributions
AFTER DELETE ON public.swarm_tracker
REFERENCING OLD TABLE AS deleted_swarm_rows
FOR EACH STATEMENT EXECUTE FUNCTION public.remove_dashboard_swarm_contributions();

-- The shared ingestion task now owns these counts. Retain the old objects for
-- compatibility, but stop redundant raw-history refresh jobs.
SELECT remove_continuous_aggregate_policy(relation, if_exists => TRUE)
FROM (VALUES (to_regclass('public.dashboard_ban_daily_peer')),
             (to_regclass('public.dashboard_ban_daily_client')),
             (to_regclass('public.dashboard_ban_daily_geo')),
             (to_regclass('public.dashboard_ban_hourly_traffic_city'))) old_aggregates(relation)
WHERE relation IS NOT NULL;
