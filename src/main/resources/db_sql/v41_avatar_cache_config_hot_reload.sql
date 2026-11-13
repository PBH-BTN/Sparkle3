UPDATE public.app_config_entries
SET hot_reload = TRUE
WHERE config_key IN (
    'sparkle.user-avatar.cache-directory',
    'sparkle.user-avatar.cache-duration',
    'sparkle.user-avatar.cleanup-cron'
);
