UPDATE public.app_config_entries
SET hot_reload = TRUE
WHERE config_key LIKE 'sparkle.ping.%';
