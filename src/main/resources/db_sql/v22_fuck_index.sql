DROP INDEX ban_history_modulename_idx;
DROP INDEX ban_history_new_populate_time_idx;
SELECT set_chunk_time_interval('ban_history', INTERVAL '1 days');
SELECT add_retention_policy('ban_history', INTERVAL '6 months');
SELECT drop_chunks('ban_history', INTERVAL '6 months');
ALTER SYSTEM SET timescaledb.enable_chunk_skipping TO on;
SELECT pg_reload_conf();
SELECT enable_chunk_skipping('ban_history', 'populate_time');
ALTER TABLE ban_history SET (
    timescaledb.compress_segmentby = 'userapps_id, torrent_id',
    timescaledb.compress_orderby = 'populate_time DESC'
    );