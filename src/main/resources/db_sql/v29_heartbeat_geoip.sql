ALTER TABLE public.userapps_heartbeat
    ADD COLUMN IF NOT EXISTS geoip jsonb;

COMMENT ON COLUMN public.userapps_heartbeat.geoip
    IS 'GeoIP snapshot for the heartbeat address, refreshed by the next heartbeat.';
