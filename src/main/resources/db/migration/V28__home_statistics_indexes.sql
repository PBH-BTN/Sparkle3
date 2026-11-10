-- Covering indexes for high-cardinality Peer ID and client-name distributions.

CREATE INDEX IF NOT EXISTS dashboard_ban_daily_peer_bucket_dimension_idx
    ON public.dashboard_ban_daily_peer (bucket DESC, peer_id)
    INCLUDE (ban_count);

CREATE INDEX IF NOT EXISTS dashboard_ban_daily_client_bucket_dimension_idx
    ON public.dashboard_ban_daily_client (bucket DESC, peer_client_name)
    INCLUDE (ban_count);

-- TimescaleDB moves continuous aggregate indexes into _timescaledb_internal,
-- so look up the real schema instead of assuming public.
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
