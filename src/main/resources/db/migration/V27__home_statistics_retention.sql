-- Drop home-statistics aggregate chunks that can no longer be queried by the UI.
-- The dashboard exposes at most 30 days; retain 35 days for late data and chunk boundaries.

SELECT add_retention_policy(
    'public.dashboard_ban_daily_peer',
    INTERVAL '35 days',
    if_not_exists => TRUE
);

SELECT add_retention_policy(
    'public.dashboard_ban_daily_client',
    INTERVAL '35 days',
    if_not_exists => TRUE
);

SELECT add_retention_policy(
    'public.dashboard_ban_daily_geo',
    INTERVAL '35 days',
    if_not_exists => TRUE
);
