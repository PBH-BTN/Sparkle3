DROP INDEX ban_history_modulename_idx;
DROP INDEX ban_history_new_populate_time_idx;
SELECT set_chunk_time_interval('ban_history', INTERVAL '7 days');
SELECT add_retention_policy('ban_history', INTERVAL '6 months');
SELECT drop_chunks('ban_history', INTERVAL '6 months');