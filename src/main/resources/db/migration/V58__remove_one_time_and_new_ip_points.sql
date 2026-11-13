-- Remove one-time and new-IP point sources. Rule-hit rewards remain, but now
-- count every accepted rule match without IP-level deduplication.

DROP FUNCTION IF EXISTS public.sparkle_mark_submitted_ips(
    BIGINT, DATE, JSONB, JSONB
);

DROP FUNCTION IF EXISTS public.sparkle_mark_rule_hit_ips(
    BIGINT, DATE, JSONB
);

DROP TABLE IF EXISTS public.point_known_ips;
DROP TABLE IF EXISTS public.point_daily_ips;

DELETE FROM public.point_claims
WHERE category IN ('NEW_TORRENT', 'CLIENT_DISCOVERY');

DELETE FROM public.point_daily_category
WHERE category IN ('NEW_IP', 'NEW_TORRENT', 'CLIENT_DISCOVERY');

DELETE FROM public.app_config_entries
WHERE config_key LIKE 'sparkle.analyse.points-once-rules.%'
   OR config_key IN (
       'sparkle.analyse.points-settings.new-ip-enabled',
       'sparkle.analyse.points-settings.new-torrent-enabled',
       'sparkle.analyse.points-settings.client-discovery-enabled',
       'sparkle.analyse.points-fixed-rules.new-ip-points',
       'sparkle.analyse.points-fixed-rules.new-ip-daily-cap'
   );
