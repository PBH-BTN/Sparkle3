-- Migration v15: Create materialized view for over-download analysis
-- This provides near-instant query performance by pre-aggregating hourly statistics
-- Expected performance: from minutes to milliseconds (95%+ improvement)

-- Drop existing view if exists (for re-running migration)
DROP MATERIALIZED VIEW IF EXISTS mv_overdownload_hourly_stats;

-- Create materialized view with hourly pre-aggregated statistics
-- This aggregates swarm_tracker data by hour, peer_ip, and torrent_id
-- Refreshed hourly to keep data current while maintaining fast query performance
CREATE MATERIALIZED VIEW mv_overdownload_hourly_stats AS
SELECT 
    -- Time dimension: truncate to hour for grouping
    date_trunc('hour', last_time_seen) AS analysis_hour,
    
    -- Dimensions
    peer_ip,
    torrent_id,
    
    -- Pre-aggregated metrics
    SUM(from_peer_traffic) AS total_downloaded,
    SUM(to_peer_traffic) AS total_uploaded,
    SUM(to_peer_traffic) - SUM(from_peer_traffic) AS pure_upload,
    
    -- Metadata
    COUNT(*) AS record_count,
    MAX(last_time_seen) AS last_seen,
    MIN(first_time_seen) AS first_seen
    
FROM swarm_tracker
WHERE 
    -- Only include rows where user uploaded more than downloaded (over-download candidates)
    (to_peer_traffic - from_peer_traffic) > 0
    -- Only recent data (last 30 days to keep view size manageable)
    AND last_time_seen >= NOW() - INTERVAL '30 days'
GROUP BY 
    date_trunc('hour', last_time_seen),
    peer_ip,
    torrent_id
WITH DATA;  -- Populate immediately

-- Create unique index to enable CONCURRENTLY refresh
-- This is required for REFRESH MATERIALIZED VIEW CONCURRENTLY
CREATE UNIQUE INDEX mv_overdownload_hourly_stats_pkey 
ON mv_overdownload_hourly_stats(analysis_hour, peer_ip, torrent_id);

-- Create index for time-based queries (most common query pattern)
-- Supports fast filtering by analysis_hour with ORDER BY pure_upload
CREATE INDEX mv_overdownload_hourly_stats_time_upload_idx 
ON mv_overdownload_hourly_stats(analysis_hour DESC, pure_upload DESC);

-- Create index for IP-based lookups
-- Useful for investigating specific IP addresses
CREATE INDEX mv_overdownload_hourly_stats_peer_ip_idx 
ON mv_overdownload_hourly_stats(peer_ip, analysis_hour DESC);

-- Create index for torrent-based analysis
CREATE INDEX mv_overdownload_hourly_stats_torrent_idx 
ON mv_overdownload_hourly_stats(torrent_id, analysis_hour DESC);

-- USAGE INSTRUCTIONS:
-- 
-- 1. Manual refresh (blocking, use during maintenance windows):
--    REFRESH MATERIALIZED VIEW mv_overdownload_hourly_stats;
--
-- 2. Concurrent refresh (non-blocking, use in production):
--    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_overdownload_hourly_stats;
--
-- 3. Query example (replaces the original complex query):
--    SELECT mv.peer_ip, mv.torrent_id, mv.total_downloaded, mv.total_uploaded, 
--           mv.pure_upload, t.size AS torrent_size
--    FROM mv_overdownload_hourly_stats mv
--    JOIN torrent t ON mv.torrent_id = t.id
--    WHERE mv.analysis_hour >= NOW() - INTERVAL '7 days'
--      AND t.size > 0
--      AND mv.pure_upload > 0
--    ORDER BY mv.pure_upload DESC
--    LIMIT 1000;
--
-- 4. Set up automatic refresh (recommended: hourly via cron or pg_cron):
--    SELECT cron.schedule('refresh-overdownload-mv', '5 * * * *', 
--           'REFRESH MATERIALIZED VIEW CONCURRENTLY mv_overdownload_hourly_stats');
--
-- MAINTENANCE:
-- - View size: monitor with SELECT pg_size_pretty(pg_total_relation_size('mv_overdownload_hourly_stats'));
-- - Refresh time: should complete in seconds with CONCURRENTLY
-- - Data retention: currently 30 days, adjust WHERE clause if needed
--
-- ROLLBACK:
-- DROP MATERIALIZED VIEW IF EXISTS mv_overdownload_hourly_stats;
