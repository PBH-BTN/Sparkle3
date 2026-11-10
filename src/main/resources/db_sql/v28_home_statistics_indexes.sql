-- Covering indexes for high-cardinality Peer ID and client-name distributions.

CREATE INDEX IF NOT EXISTS dashboard_ban_daily_peer_bucket_dimension_idx
    ON public.dashboard_ban_daily_peer (bucket DESC, peer_id)
    INCLUDE (ban_count);

CREATE INDEX IF NOT EXISTS dashboard_ban_daily_client_bucket_dimension_idx
    ON public.dashboard_ban_daily_client (bucket DESC, peer_client_name)
    INCLUDE (ban_count);

DROP INDEX IF EXISTS public.dashboard_ban_daily_peer_bucket_idx;
DROP INDEX IF EXISTS public.dashboard_ban_daily_client_bucket_idx;
