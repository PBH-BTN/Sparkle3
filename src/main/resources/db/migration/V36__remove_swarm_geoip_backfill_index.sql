-- Remove the partial index created for the discontinued GeoIP callback.
DROP INDEX CONCURRENTLY IF EXISTS public.swarm_tracker_null_geoip_peer_idx;
