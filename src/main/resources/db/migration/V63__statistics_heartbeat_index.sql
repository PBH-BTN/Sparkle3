-- Build only the lookup index needed by application ingestion; no historical data copy.
CREATE INDEX IF NOT EXISTS userapps_heartbeat_lifetime_ip_idx
    ON public.userapps_heartbeat (ip, first_seen_at, last_seen_at);
