-- Home dashboard network statistics.
-- Continuous aggregates keep common 7/14/30 day queries off the raw ban_history table.

CREATE MATERIALIZED VIEW IF NOT EXISTS public.dashboard_ban_daily_peer
WITH (timescaledb.continuous) AS
SELECT
    time_bucket(INTERVAL '1 day', populate_time) AS bucket,
    COALESCE(NULLIF(peer_id, ''), 'Unknown') AS peer_id,
    COUNT(*)::bigint AS ban_count
FROM public.ban_history
GROUP BY bucket, peer_id
WITH NO DATA;

CREATE INDEX IF NOT EXISTS dashboard_ban_daily_peer_bucket_idx
    ON public.dashboard_ban_daily_peer (bucket DESC, ban_count DESC)
    INCLUDE (peer_id);

ALTER MATERIALIZED VIEW public.dashboard_ban_daily_peer SET (
    timescaledb.compress = true,
    timescaledb.compress_orderby = 'bucket DESC, peer_id'
);

SELECT add_compression_policy(
    'public.dashboard_ban_daily_peer',
    INTERVAL '14 days',
    if_not_exists => TRUE
);

SELECT add_continuous_aggregate_policy(
    'public.dashboard_ban_daily_peer',
    start_offset => INTERVAL '3 days',
    end_offset => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour',
    if_not_exists => TRUE
);

CREATE MATERIALIZED VIEW IF NOT EXISTS public.dashboard_ban_daily_client
WITH (timescaledb.continuous) AS
SELECT
    time_bucket(INTERVAL '1 day', populate_time) AS bucket,
    COALESCE(NULLIF(peer_client_name, ''), 'Unknown') AS peer_client_name,
    COUNT(*)::bigint AS ban_count
FROM public.ban_history
GROUP BY bucket, peer_client_name
WITH NO DATA;

CREATE INDEX IF NOT EXISTS dashboard_ban_daily_client_bucket_idx
    ON public.dashboard_ban_daily_client (bucket DESC, ban_count DESC)
    INCLUDE (peer_client_name);

ALTER MATERIALIZED VIEW public.dashboard_ban_daily_client SET (
    timescaledb.compress = true,
    timescaledb.compress_orderby = 'bucket DESC, peer_client_name'
);

SELECT add_compression_policy(
    'public.dashboard_ban_daily_client',
    INTERVAL '14 days',
    if_not_exists => TRUE
);

SELECT add_continuous_aggregate_policy(
    'public.dashboard_ban_daily_client',
    start_offset => INTERVAL '3 days',
    end_offset => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour',
    if_not_exists => TRUE
);

CREATE MATERIALIZED VIEW IF NOT EXISTS public.dashboard_ban_daily_geo
WITH (timescaledb.continuous) AS
SELECT
    time_bucket(INTERVAL '1 day', populate_time) AS bucket,
    COALESCE(
        NULLIF(peer_geoip #>> '{city,cnCity}', ''),
        NULLIF(peer_geoip #>> '{city,name}', ''),
        NULLIF(peer_geoip #>> '{country,name}', ''),
        'Unknown'
    ) AS city,
    family(peer_ip) AS ip_version,
    COUNT(*)::bigint AS ban_count
FROM public.ban_history
GROUP BY bucket, city, ip_version
WITH NO DATA;

CREATE INDEX IF NOT EXISTS dashboard_ban_daily_geo_bucket_idx
    ON public.dashboard_ban_daily_geo (bucket DESC, ip_version, city)
    INCLUDE (ban_count);

ALTER MATERIALIZED VIEW public.dashboard_ban_daily_geo SET (
    timescaledb.compress = true,
    timescaledb.compress_segmentby = 'ip_version',
    timescaledb.compress_orderby = 'bucket DESC, city'
);

SELECT add_compression_policy(
    'public.dashboard_ban_daily_geo',
    INTERVAL '14 days',
    if_not_exists => TRUE
);

SELECT add_continuous_aggregate_policy(
    'public.dashboard_ban_daily_geo',
    start_offset => INTERVAL '3 days',
    end_offset => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour',
    if_not_exists => TRUE
);

CREATE INDEX IF NOT EXISTS userapp_active_useragent_idx
    ON public.userapp (last_seen_at)
    WHERE banned_at IS NULL
      AND last_user_agent IS NOT NULL
      AND last_user_agent <> '';

CALL refresh_continuous_aggregate(
    'public.dashboard_ban_daily_peer',
    NOW() - INTERVAL '32 days',
    NOW()
);

CALL refresh_continuous_aggregate(
    'public.dashboard_ban_daily_client',
    NOW() - INTERVAL '32 days',
    NOW()
);

CALL refresh_continuous_aggregate(
    'public.dashboard_ban_daily_geo',
    NOW() - INTERVAL '32 days',
    NOW()
);
