-- Manual installation variant of V35__incremental_bittorrent_traffic.sql.

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

CREATE TABLE IF NOT EXISTS public.dashboard_ban_city_hourly_traffic (
    hour TIMESTAMPTZ NOT NULL,
    city TEXT NOT NULL,
    upload_bytes NUMERIC(30, 0) NOT NULL,
    download_bytes NUMERIC(30, 0) NOT NULL,
    PRIMARY KEY (hour, city)
);

DELETE FROM public.dashboard_ban_city_traffic
WHERE range_days NOT IN (1, 3, 7);

ALTER TABLE public.dashboard_ban_city_traffic
    DROP CONSTRAINT IF EXISTS dashboard_ban_city_traffic_range_check;

ALTER TABLE public.dashboard_ban_city_traffic
    ADD CONSTRAINT dashboard_ban_city_traffic_range_check
        CHECK (range_days IN (1, 3, 7));

CREATE TABLE IF NOT EXISTS public.dashboard_bittorrent_traffic_refresh_state (
    task VARCHAR(64) PRIMARY KEY,
    backfill_cursor TIMESTAMPTZ NOT NULL,
    watermark TIMESTAMPTZ NOT NULL,
    ready BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION public.refresh_dashboard_ban_city_hourly_traffic()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        INSERT INTO public.dashboard_ban_city_hourly_traffic (
            hour, city, upload_bytes, download_bytes
        )
        VALUES (
            time_bucket(INTERVAL '1 hour', OLD.last_seen_at),
            OLD.city,
            -OLD.upload_bytes,
            -OLD.download_bytes
        )
        ON CONFLICT (hour, city) DO UPDATE
        SET upload_bytes =
                dashboard_ban_city_hourly_traffic.upload_bytes + EXCLUDED.upload_bytes,
            download_bytes =
                dashboard_ban_city_hourly_traffic.download_bytes + EXCLUDED.download_bytes;
    END IF;

    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        INSERT INTO public.dashboard_ban_city_hourly_traffic (
            hour, city, upload_bytes, download_bytes
        )
        VALUES (
            time_bucket(INTERVAL '1 hour', NEW.last_seen_at),
            NEW.city,
            NEW.upload_bytes,
            NEW.download_bytes
        )
        ON CONFLICT (hour, city) DO UPDATE
        SET upload_bytes =
                dashboard_ban_city_hourly_traffic.upload_bytes + EXCLUDED.upload_bytes,
            download_bytes =
                dashboard_ban_city_hourly_traffic.download_bytes + EXCLUDED.download_bytes;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER dashboard_ban_connection_traffic_hourly_trigger
AFTER INSERT OR UPDATE OR DELETE
ON public.dashboard_ban_connection_traffic
FOR EACH ROW
EXECUTE FUNCTION public.refresh_dashboard_ban_city_hourly_traffic();

ALTER TABLE public.dashboard_ban_connection_traffic
    DISABLE TRIGGER dashboard_ban_connection_traffic_hourly_trigger;

INSERT INTO public.dashboard_bittorrent_traffic_refresh_state (
    task, backfill_cursor, watermark, ready
)
VALUES (
    'ban-connections',
    time_bucket(INTERVAL '1 hour', NOW()) + INTERVAL '1 hour' - INTERVAL '7 days',
    NOW(),
    FALSE
)
ON CONFLICT (task) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.dashboard_ban_connection_traffic LIMIT 1) THEN
        TRUNCATE TABLE public.dashboard_ban_city_hourly_traffic;

        INSERT INTO public.dashboard_ban_city_hourly_traffic (
            hour, city, upload_bytes, download_bytes
        )
        SELECT
            time_bucket(INTERVAL '1 hour', last_seen_at),
            city,
            SUM(upload_bytes)::numeric,
            SUM(download_bytes)::numeric
        FROM public.dashboard_ban_connection_traffic
        WHERE last_seen_at >= NOW() - INTERVAL '8 days'
        GROUP BY time_bucket(INTERVAL '1 hour', last_seen_at), city;

        ALTER TABLE public.dashboard_ban_connection_traffic
            ENABLE TRIGGER dashboard_ban_connection_traffic_hourly_trigger;

        UPDATE public.dashboard_bittorrent_traffic_refresh_state
        SET backfill_cursor = NOW(),
            watermark = NOW(),
            ready = TRUE,
            updated_at = NOW()
        WHERE task = 'ban-connections';
    END IF;
END
$$;
