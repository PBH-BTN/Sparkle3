-- Remove only the retired dashboard's derived data. Raw reports and the
-- independent homepage count aggregates / personal statistics are retained.
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
    'sparkle.homepage-statistics-refresh.modules.bittorrent-traffic.refresh-interval-minutes'
);
