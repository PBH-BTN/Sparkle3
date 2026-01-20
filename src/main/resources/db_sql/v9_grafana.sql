CREATE INDEX idx_ban_history_cidr_fixed ON ban_history (
                                                        (CASE
                                                             WHEN family(peer_ip) = 4
                                                                 THEN network(set_masklen(peer_ip, 32))
                                                             WHEN family(peer_ip) = 6
                                                                 THEN network(set_masklen(peer_ip, 56))
                                                             ELSE NULL::cidr
                                                            END)
    );

CREATE MATERIALIZED VIEW public.grafana_banhistory_most_ban_14days
AS
WITH top_100_ips AS (SELECT
                         -- 使用 network() 强制将主机位置零
                         CASE
                             WHEN family(peer_ip) = 4 THEN network(set_masklen(peer_ip, 32))
                             WHEN family(peer_ip) = 6 THEN network(set_masklen(peer_ip, 56))
                             END                             AS masked_ip,
                         COUNT(*)                            AS record_count,
                         GREATEST(SUM(to_peer_traffic), 0)   AS total_to_peer_traffic,
                         GREATEST(SUM(from_peer_traffic), 0) AS total_from_peer_traffic
                     FROM "public"."ban_history"
                     WHERE populate_time >= NOW() - INTERVAL '14 days'
                     GROUP BY 1
                     ORDER BY record_count DESC
                     LIMIT 100)
SELECT t.*,
       geo.details ->> 'cityName' as city_name,
       geo.details ->> 'isp'      as isp
FROM top_100_ips t
         LEFT JOIN LATERAL (
    SELECT b.peer_geoip as details
    FROM ban_history b
    WHERE b.populate_time >= NOW() - INTERVAL '14 days'
      AND (CASE
               WHEN family(b.peer_ip) = 4 THEN network(set_masklen(b.peer_ip, 32))
               WHEN family(b.peer_ip) = 6 THEN network(set_masklen(b.peer_ip, 56))
        END) = t.masked_ip
    LIMIT 1
    ) geo ON true
WITH NO DATA;


CREATE MATERIALIZED VIEW public.grafana_banhistory_most_ban_14days_pcb
AS
WITH top_100_ips AS (SELECT
                         -- 使用 network() 强制将主机位置零
                         CASE
                             WHEN family(peer_ip) = 4 THEN network(set_masklen(peer_ip, 32))
                             WHEN family(peer_ip) = 6 THEN network(set_masklen(peer_ip, 56))
                             END                             AS masked_ip,
                         COUNT(*)                            AS record_count,
                         GREATEST(SUM(to_peer_traffic), 0)   AS total_to_peer_traffic,
                         GREATEST(SUM(from_peer_traffic), 0) AS total_from_peer_traffic
                     FROM "public"."ban_history"
                     WHERE populate_time >= NOW() - INTERVAL '14 days'
                     AND module_name = 'com.ghostchu.peerbanhelper.module.impl.rule.ProgressCheatBlocker'
                     GROUP BY 1
                     ORDER BY record_count DESC
                     LIMIT 100)
SELECT t.*,
       geo.details ->> 'cityName' as city_name,
       geo.details ->> 'isp'      as isp
FROM top_100_ips t
         LEFT JOIN LATERAL (
    SELECT b.peer_geoip as details
    FROM ban_history b
    WHERE b.populate_time >= NOW() - INTERVAL '14 days'
      AND (CASE
               WHEN family(b.peer_ip) = 4 THEN network(set_masklen(b.peer_ip, 32))
               WHEN family(b.peer_ip) = 6 THEN network(set_masklen(b.peer_ip, 56))
        END) = t.masked_ip
    LIMIT 1
    ) geo ON true
WITH NO DATA;
