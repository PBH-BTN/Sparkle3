-- Flyway adoption point for installations created before Flyway was enabled.
--
-- Every statement is idempotent. The migration repairs databases where only
-- some of the old db_sql/v23-v29 scripts had been run manually, without ever
-- dropping or rewriting application data.

CREATE TABLE IF NOT EXISTS public.app_config_entries (
    config_key VARCHAR(512) PRIMARY KEY,
    config_value TEXT NOT NULL,
    value_type VARCHAR(32) NOT NULL DEFAULT 'string',
    config_group VARCHAR(128) NOT NULL DEFAULT 'other',
    source VARCHAR(32) NOT NULL DEFAULT 'yaml',
    sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    hot_reload BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT
);

CREATE INDEX IF NOT EXISTS app_config_entries_group_idx
    ON public.app_config_entries (config_group);

CREATE TABLE IF NOT EXISTS public.announcements (
    id BIGSERIAL PRIMARY KEY,
    level VARCHAR(16) NOT NULL DEFAULT 'BLUE',
    title_zh TEXT NOT NULL DEFAULT '',
    title_en TEXT NOT NULL DEFAULT '',
    content_zh TEXT NOT NULL DEFAULT '',
    content_en TEXT NOT NULL DEFAULT '',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    dismissible BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT
);

CREATE INDEX IF NOT EXISTS announcements_active_idx
    ON public.announcements (enabled, starts_at, ends_at, sort_order DESC);

CREATE INDEX IF NOT EXISTS swarm_tracker_last_seen_userapps_idx
    ON public.swarm_tracker (last_time_seen DESC, userapps_id);
CREATE INDEX IF NOT EXISTS swarm_tracker_userapps_last_seen_idx
    ON public.swarm_tracker (userapps_id, last_time_seen DESC);
CREATE INDEX IF NOT EXISTS userapps_heartbeat_last_seen_userapp_idx
    ON public.userapps_heartbeat (last_seen_at DESC, userapp_id, ip);
CREATE INDEX IF NOT EXISTS userapp_owner_idx
    ON public.userapp (owner);

CREATE TABLE IF NOT EXISTS public.navigation_items (
    id VARCHAR(128) PRIMARY KEY,
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    label_zh VARCHAR(100) NOT NULL,
    label_en VARCHAR(100) NOT NULL,
    href VARCHAR(2048) NOT NULL,
    icon VARCHAR(32) NOT NULL DEFAULT 'link',
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    open_in_new_tab BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT
);

CREATE INDEX IF NOT EXISTS navigation_items_order_idx
    ON public.navigation_items (enabled, sort_order);

CREATE MATERIALIZED VIEW IF NOT EXISTS public.dashboard_ban_daily_peer
WITH (timescaledb.continuous) AS
SELECT
    time_bucket(INTERVAL '1 day', populate_time) AS bucket,
    COALESCE(NULLIF(peer_id, ''), 'Unknown') AS peer_id,
    COUNT(*)::bigint AS ban_count
FROM public.ban_history
GROUP BY bucket, peer_id
WITH NO DATA;

CREATE INDEX IF NOT EXISTS dashboard_ban_daily_peer_bucket_dimension_idx
    ON public.dashboard_ban_daily_peer (bucket DESC, peer_id)
    INCLUDE (ban_count);

ALTER MATERIALIZED VIEW public.dashboard_ban_daily_peer SET (
    timescaledb.compress = TRUE,
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

SELECT add_retention_policy(
    'public.dashboard_ban_daily_peer',
    INTERVAL '35 days',
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

CREATE INDEX IF NOT EXISTS dashboard_ban_daily_client_bucket_dimension_idx
    ON public.dashboard_ban_daily_client (bucket DESC, peer_client_name)
    INCLUDE (ban_count);

ALTER MATERIALIZED VIEW public.dashboard_ban_daily_client SET (
    timescaledb.compress = TRUE,
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

SELECT add_retention_policy(
    'public.dashboard_ban_daily_client',
    INTERVAL '35 days',
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
    timescaledb.compress = TRUE,
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

SELECT add_retention_policy(
    'public.dashboard_ban_daily_geo',
    INTERVAL '35 days',
    if_not_exists => TRUE
);

DO $$
DECLARE
    index_record RECORD;
BEGIN
    FOR index_record IN
        SELECT namespace.nspname, relation.relname
        FROM pg_catalog.pg_class relation
        JOIN pg_catalog.pg_namespace namespace ON namespace.oid = relation.relnamespace
        WHERE relation.relkind = 'i'
          AND relation.relname IN (
              'dashboard_ban_daily_peer_bucket_idx',
              'dashboard_ban_daily_client_bucket_idx'
          )
    LOOP
        EXECUTE format(
            'DROP INDEX IF EXISTS %I.%I',
            index_record.nspname,
            index_record.relname
        );
    END LOOP;
END
$$;

CREATE INDEX IF NOT EXISTS userapp_active_useragent_idx
    ON public.userapp (last_seen_at)
    WHERE banned_at IS NULL
      AND last_user_agent IS NOT NULL
      AND last_user_agent <> '';

ALTER TABLE public.userapps_heartbeat
    ADD COLUMN IF NOT EXISTS geoip JSONB;

COMMENT ON COLUMN public.userapps_heartbeat.geoip
    IS 'GeoIP snapshot for the heartbeat address, refreshed by the next heartbeat.';
