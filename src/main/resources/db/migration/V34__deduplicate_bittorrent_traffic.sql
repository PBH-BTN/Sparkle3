-- Ban traffic counters are cumulative snapshots for one peer connection.
-- Repeated ban reports for the same connection must be counted once.

SELECT remove_continuous_aggregate_policy(
    'public.dashboard_ban_hourly_traffic_city'::regclass,
    if_not_exists => TRUE,
    if_exists => TRUE
);

SELECT remove_retention_policy(
    'public.dashboard_ban_hourly_traffic_city'::regclass,
    if_exists => TRUE
);

SELECT remove_compression_policy(
    'public.dashboard_ban_hourly_traffic_city'::regclass,
    if_exists => TRUE
);

-- Keep the old aggregate object to avoid racing an active refresh. Its
-- policies are removed above and it is no longer used by the application.

CREATE TABLE IF NOT EXISTS public.dashboard_ban_connection_traffic (
    userapps_id BIGINT NOT NULL,
    torrent_id BIGINT NOT NULL,
    peer_ip INET NOT NULL,
    peer_port INTEGER NOT NULL,
    peer_id VARCHAR(20) NOT NULL,
    city TEXT NOT NULL,
    upload_bytes BIGINT NOT NULL,
    download_bytes BIGINT NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (userapps_id, torrent_id, peer_ip, peer_port, peer_id)
);

CREATE INDEX IF NOT EXISTS dashboard_ban_connection_traffic_last_seen_idx
    ON public.dashboard_ban_connection_traffic (last_seen_at);

INSERT INTO public.dashboard_ban_connection_traffic (
    userapps_id,
    torrent_id,
    peer_ip,
    peer_port,
    peer_id,
    city,
    upload_bytes,
    download_bytes,
    last_seen_at
)
SELECT
    ban_history.userapps_id,
    ban_history.torrent_id,
    ban_history.peer_ip,
    ban_history.peer_port,
    COALESCE(ban_history.peer_id, '') AS peer_id,
    MAX(COALESCE(
        NULLIF(ban_history.peer_geoip #>> '{city,cnCity}', ''),
        NULLIF(ban_history.peer_geoip #>> '{city,name}', ''),
        NULLIF(ban_history.peer_geoip #>> '{country,name}', ''),
        'Unknown'
    )) AS city,
    MAX(GREATEST(ban_history.to_peer_traffic, 0)) AS upload_bytes,
    MAX(GREATEST(ban_history.from_peer_traffic, 0)) AS download_bytes,
    MAX(ban_history.populate_time) AS last_seen_at
FROM public.ban_history
WHERE ban_history.populate_time >=
          time_bucket(INTERVAL '1 hour', NOW()) + INTERVAL '1 hour' - INTERVAL '7 days'
  AND ban_history.populate_time <
          time_bucket(INTERVAL '1 hour', NOW()) + INTERVAL '1 hour'
GROUP BY
    ban_history.userapps_id,
    ban_history.torrent_id,
    ban_history.peer_ip,
    ban_history.peer_port,
    COALESCE(ban_history.peer_id, '')
ON CONFLICT (
    userapps_id,
    torrent_id,
    peer_ip,
    peer_port,
    peer_id
) DO UPDATE
SET city = EXCLUDED.city,
    upload_bytes = EXCLUDED.upload_bytes,
    download_bytes = EXCLUDED.download_bytes,
    last_seen_at = EXCLUDED.last_seen_at
WHERE EXCLUDED.last_seen_at >= dashboard_ban_connection_traffic.last_seen_at;

CREATE TABLE IF NOT EXISTS public.dashboard_ban_city_traffic (
    range_days SMALLINT NOT NULL,
    city TEXT NOT NULL,
    upload_bytes NUMERIC(30, 0) NOT NULL,
    download_bytes NUMERIC(30, 0) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (range_days, city),
    CONSTRAINT dashboard_ban_city_traffic_range_check
        CHECK (range_days IN (1, 3, 7))
);

INSERT INTO public.dashboard_ban_city_traffic (
    range_days,
    city,
    upload_bytes,
    download_bytes,
    generated_at
)
WITH bounds AS (
    SELECT
        time_bucket(INTERVAL '1 hour', NOW()) + INTERVAL '1 hour' AS range_end
),
city_windows AS (
    SELECT
        latest.city,
        COALESCE(SUM(upload_bytes) FILTER (
            WHERE last_seen_at >= bounds.range_end - INTERVAL '1 day'
        ), 0)::numeric AS upload_1d,
        COALESCE(SUM(download_bytes) FILTER (
            WHERE last_seen_at >= bounds.range_end - INTERVAL '1 day'
        ), 0)::numeric AS download_1d,
        COALESCE(SUM(upload_bytes) FILTER (
            WHERE last_seen_at >= bounds.range_end - INTERVAL '3 days'
        ), 0)::numeric AS upload_3d,
        COALESCE(SUM(download_bytes) FILTER (
            WHERE last_seen_at >= bounds.range_end - INTERVAL '3 days'
        ), 0)::numeric AS download_3d,
        COALESCE(SUM(upload_bytes), 0)::numeric AS upload_7d,
        COALESCE(SUM(download_bytes), 0)::numeric AS download_7d
    FROM dashboard_ban_connection_traffic latest
    CROSS JOIN bounds
    WHERE latest.last_seen_at >= bounds.range_end - INTERVAL '7 days'
    GROUP BY latest.city, bounds.range_end
)
SELECT
    ranges.range_days,
    city_windows.city,
    ranges.upload_bytes,
    ranges.download_bytes,
    NOW()
FROM city_windows
CROSS JOIN LATERAL (
    VALUES
        (1::SMALLINT, upload_1d, download_1d),
        (3::SMALLINT, upload_3d, download_3d),
        (7::SMALLINT, upload_7d, download_7d)
) AS ranges(range_days, upload_bytes, download_bytes)
WHERE ranges.upload_bytes > 0
   OR ranges.download_bytes > 0
ON CONFLICT (range_days, city) DO UPDATE
SET upload_bytes = EXCLUDED.upload_bytes,
    download_bytes = EXCLUDED.download_bytes,
    generated_at = EXCLUDED.generated_at;

DELETE FROM public.dashboard_ban_city_traffic
WHERE range_days NOT IN (1, 3, 7);

UPDATE public.app_config_entries
SET config_value = '60',
    updated_at = NOW()
WHERE config_key = 'sparkle.homepage-statistics-refresh.modules.bittorrent-traffic.refresh-interval-minutes'
  AND source = 'yaml';
