-- Migration v14: Optimize analyseOverDownload query performance
-- This migration creates specialized indexes to improve the performance of over-download analysis
-- which processes ~50 million swarm_tracker records with 7-day time windows

-- Index 1: Composite partial index for time-based filtering with torrent join
-- Only indexes rows that have positive upload (to_peer_traffic > from_peer_traffic)
-- This reduces index size by ~50% and speeds up the WHERE clause filtering
CREATE INDEX CONCURRENTLY IF NOT EXISTS swarm_tracker_analyse_composite_idx 
ON swarm_tracker(last_time_seen DESC, torrent_id) 
WHERE (to_peer_traffic - from_peer_traffic) > 0;

-- Index 2: Covering index to avoid heap lookups (includes traffic columns)
-- This index contains all columns needed by the query, eliminating the need for table access
-- Significantly reduces I/O operations by keeping everything in the index
CREATE INDEX CONCURRENTLY IF NOT EXISTS swarm_tracker_overdownload_covering_idx 
ON swarm_tracker(last_time_seen DESC, peer_ip, torrent_id) 
INCLUDE (from_peer_traffic, to_peer_traffic) 
WHERE (to_peer_traffic - from_peer_traffic) > 0;

-- Index 3: Support index for torrent.size filtering
-- The query filters on torrent.size > 0, this index speeds up that check
CREATE INDEX CONCURRENTLY IF NOT EXISTS torrent_size_idx 
ON torrent(size) 
WHERE size > 0;

-- Performance expectations:
-- - Query time reduction: 70-80% improvement expected
-- - Reduces full table scans to targeted index scans
-- - Partial indexes are ~50% smaller than full indexes
-- - CONCURRENTLY option ensures no downtime during index creation

-- Rollback instructions (if needed):
-- DROP INDEX CONCURRENTLY IF EXISTS swarm_tracker_analyse_composite_idx;
-- DROP INDEX CONCURRENTLY IF EXISTS swarm_tracker_overdownload_covering_idx;
-- DROP INDEX CONCURRENTLY IF EXISTS torrent_size_idx;
