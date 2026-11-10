CREATE TABLE IF NOT EXISTS public.announcements (
    id BIGSERIAL PRIMARY KEY,
    level VARCHAR(16) NOT NULL DEFAULT 'BLUE',
    title_zh TEXT NOT NULL DEFAULT '',
    title_en TEXT NOT NULL DEFAULT '',
    content_zh TEXT NOT NULL DEFAULT '',
    content_en TEXT NOT NULL DEFAULT '',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    dismissible BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT
);

CREATE INDEX IF NOT EXISTS announcements_active_idx
    ON public.announcements (enabled, starts_at, ends_at, sort_order DESC);

-- Seven-day swarm statistics uses last_time_seen as its window boundary.
-- TimescaleDB hypertables do not support CREATE INDEX CONCURRENTLY.
-- Run this migration during a maintenance window on production databases.
CREATE INDEX IF NOT EXISTS swarm_tracker_last_seen_userapps_idx
    ON public.swarm_tracker (last_time_seen DESC, userapps_id);

CREATE INDEX IF NOT EXISTS swarm_tracker_userapps_last_seen_idx
    ON public.swarm_tracker (userapps_id, last_time_seen DESC);

CREATE INDEX IF NOT EXISTS userapps_heartbeat_last_seen_userapp_idx
    ON public.userapps_heartbeat (last_seen_at DESC, userapp_id, ip);

CREATE INDEX IF NOT EXISTS userapp_owner_idx
    ON public.userapp (owner);
