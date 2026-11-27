-- Remove incomplete builds left by an interrupted attempt before retrying this migration.
DROP INDEX CONCURRENTLY IF EXISTS public.user_career_events_expiry_idx;
CREATE INDEX CONCURRENTLY user_career_events_expiry_idx
    ON public.user_career_events (last_seen);
DROP INDEX CONCURRENTLY IF EXISTS public.user_lifetime_traffic_checkpoint_expiry_idx;
CREATE INDEX CONCURRENTLY user_lifetime_traffic_checkpoint_expiry_idx
    ON public.user_lifetime_traffic_checkpoint (last_seen);
