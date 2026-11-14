-- Remove only the retired dashboard's derived data. Raw reports and the
-- independent homepage count aggregates / personal statistics are retained.
DROP TRIGGER IF EXISTS dashboard_swarm_insert_changes ON public.swarm_tracker;
DROP TRIGGER IF EXISTS dashboard_swarm_update_changes ON public.swarm_tracker;
DROP TRIGGER IF EXISTS dashboard_swarm_remove_contributions ON public.swarm_tracker;
DROP FUNCTION IF EXISTS public.mark_dashboard_swarm_changes();
DROP FUNCTION IF EXISTS public.remove_dashboard_swarm_contributions();
DROP TABLE IF EXISTS public.dashboard_traffic_swarm_changes;
DROP TABLE IF EXISTS public.dashboard_traffic_swarm_checkpoint;
DROP TABLE IF EXISTS public.dashboard_traffic_swarm_hourly;
DROP TABLE IF EXISTS public.dashboard_traffic_counts;
DROP TABLE IF EXISTS public.dashboard_traffic_ban_event_checkpoint;
DROP TABLE IF EXISTS public.dashboard_traffic_ban_ranking;
DROP TABLE IF EXISTS public.dashboard_traffic_incremental_state;

DROP MATERIALIZED VIEW IF EXISTS public.dashboard_ban_hourly_traffic_city;
DROP TABLE IF EXISTS public.dashboard_ban_connection_traffic;
DROP FUNCTION IF EXISTS public.refresh_dashboard_ban_city_hourly_traffic();
DROP TABLE IF EXISTS public.dashboard_ban_city_hourly_traffic;
DROP TABLE IF EXISTS public.dashboard_ban_city_traffic;
DROP TABLE IF EXISTS public.dashboard_swarm_hourly_traffic;
DROP TABLE IF EXISTS public.dashboard_bittorrent_traffic_refresh_state;

DELETE FROM public.app_config_entries
WHERE config_key IN (
    'sparkle.homepage-statistics-refresh.modules.bittorrent-traffic.enabled',
    'sparkle.homepage-statistics-refresh.modules.bittorrent-traffic.refresh-interval-minutes',
    'sparkle.homepage-statistics-refresh.modules.bittorrent-traffic-data.enabled',
    'sparkle.homepage-statistics-refresh.modules.bittorrent-traffic-data.refresh-interval-minutes',
    'sparkle.homepage-statistics-refresh.modules.bittorrent-traffic-rankings.enabled',
    'sparkle.homepage-statistics-refresh.modules.bittorrent-traffic-rankings.refresh-interval-minutes'
);

-- V68 disabled these independent count aggregate policies. The retained home
-- charts use the original aggregates again, so restore their original schedule.
SELECT add_continuous_aggregate_policy(
    relation, start_offset => INTERVAL '3 days', end_offset => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour', if_not_exists => TRUE
)
FROM (VALUES ('public.dashboard_ban_daily_peer'::regclass),
             ('public.dashboard_ban_daily_client'::regclass),
             ('public.dashboard_ban_daily_geo'::regclass)) aggregates(relation);
