ALTER TABLE public.announcements
    ADD COLUMN IF NOT EXISTS placement VARCHAR(32) NOT NULL DEFAULT 'GLOBAL_TOP';

COMMENT ON COLUMN public.announcements.placement
    IS 'Announcement display location: GLOBAL_TOP or HOME_HERO.';

CREATE INDEX IF NOT EXISTS announcements_active_placement_idx
    ON public.announcements (
        placement,
        enabled,
        starts_at,
        ends_at,
        sort_order DESC
    );
