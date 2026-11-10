-- BitTorrent traffic dashboards.
--
-- Ban events are aggregated by TimescaleDB. Swarm tracker rows are compact
-- hourly snapshots because swarm_tracker is a regular upsert table with a
-- shorter retention window than the dashboard.

CREATE MATERIALIZED VIEW IF NOT EXISTS public.dashboard_ban_hourly_traffic_city
WITH (timescaledb.continuous) AS
SELECT
    time_bucket(INTERVAL '1 hour', populate_time) AS bucket,
    COALESCE(
        NULLIF(peer_geoip #>> '{city,cnCity}', ''),
        NULLIF(peer_geoip #>> '{city,name}', ''),
        NULLIF(peer_geoip #>> '{country,name}', ''),
        'Unknown'
    ) AS city,
    SUM(GREATEST(to_peer_traffic, 0))::numeric AS upload_bytes,
    SUM(GREATEST(from_peer_traffic, 0))::numeric AS download_bytes
FROM public.ban_history
GROUP BY bucket, city
WITH NO DATA;

CREATE INDEX IF NOT EXISTS dashboard_ban_hourly_traffic_city_bucket_idx
    ON public.dashboard_ban_hourly_traffic_city (bucket DESC, city)
    INCLUDE (upload_bytes, download_bytes);

ALTER MATERIALIZED VIEW public.dashboard_ban_hourly_traffic_city SET (
    timescaledb.compress = true,
    timescaledb.compress_segmentby = 'city',
    timescaledb.compress_orderby = 'bucket DESC'
);

-- The refresh policy can start immediately after it is registered. Real-time
-- aggregation keeps the dashboard readable while that first refresh runs.
ALTER MATERIALIZED VIEW public.dashboard_ban_hourly_traffic_city
    SET (timescaledb.materialized_only = false);

SELECT add_compression_policy(
    'public.dashboard_ban_hourly_traffic_city',
    INTERVAL '14 days',
    if_not_exists => TRUE
);

SELECT add_continuous_aggregate_policy(
    'public.dashboard_ban_hourly_traffic_city',
    start_offset => INTERVAL '3 days',
    end_offset => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour',
    if_not_exists => TRUE
);

SELECT add_retention_policy(
    'public.dashboard_ban_hourly_traffic_city',
    INTERVAL '35 days',
    if_not_exists => TRUE
);

CREATE TABLE IF NOT EXISTS public.dashboard_swarm_hourly_traffic (
    bucket TIMESTAMPTZ NOT NULL,
    city TEXT NOT NULL,
    upload_bytes NUMERIC(30, 0) NOT NULL,
    download_bytes NUMERIC(30, 0) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (bucket, city)
);

INSERT INTO public.dashboard_swarm_hourly_traffic (
    bucket,
    city,
    upload_bytes,
    download_bytes,
    refreshed_at
)
SELECT
    time_bucket(INTERVAL '1 hour', last_time_seen) AS bucket,
    COALESCE(
        NULLIF(peer_geoip #>> '{city,cnCity}', ''),
        NULLIF(peer_geoip #>> '{city,name}', ''),
        NULLIF(peer_geoip #>> '{country,name}', ''),
        'Unknown'
    ) AS city,
    SUM(GREATEST(to_peer_traffic, 0))::numeric AS upload_bytes,
    SUM(GREATEST(from_peer_traffic, 0))::numeric AS download_bytes,
    NOW()
FROM public.swarm_tracker
WHERE last_time_seen >= NOW() - INTERVAL '8 days'
  AND last_time_seen < NOW() + INTERVAL '1 hour'
GROUP BY bucket, city
ON CONFLICT (bucket, city) DO UPDATE
SET upload_bytes = EXCLUDED.upload_bytes,
    download_bytes = EXCLUDED.download_bytes,
    refreshed_at = EXCLUDED.refreshed_at;
