CREATE INDEX CONCURRENTLY IF NOT EXISTS swarm_tracker_null_geoip_peer_idx
    ON public.swarm_tracker (peer_ip)
    WHERE peer_geoip IS NULL;
