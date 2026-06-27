DROP MATERIALIZED VIEW analyse_ban_history_hourly_basic;

CREATE MATERIALIZED VIEW IF NOT EXISTS analyse_ban_history_hourly_basic
WITH (timescaledb.continuous) AS
SELECT
    -- 按 1 小时进行时间分组
    time_bucket(INTERVAL '1 hour', populate_time) AS bucket,
    -- 1. 每小时封禁记录数量
    COUNT(*) AS total_bans,
    -- 2. 每小时所有的 to_peer_traffic 总和
    SUM(to_peer_traffic) AS total_to_peer_traffic,
    -- 3. 每小时所有的 from_peer_traffic 总和
    SUM(from_peer_traffic) AS total_from_peer_traffic
FROM ban_history
GROUP BY bucket
    WITH NO DATA;

ALTER MATERIALIZED VIEW analyse_ban_history_hourly_basic SET (timescaledb.compress = true);

SELECT add_continuous_aggregate_policy('analyse_ban_history_hourly_basic',
                                       start_offset => INTERVAL '3 days',    -- 每次向前检查并刷新最近 3 天内可能发生变化的数据
                                       end_offset => INTERVAL '1 hour',      -- 不刷新当前最近 1 小时内还在变化的数据（避免频繁锁表，交给实时聚合）
                                       schedule_interval => INTERVAL '1 hour' -- 每 1 小时自动执行一次刷新任务
       );


CALL refresh_continuous_aggregate('analyse_ban_history_hourly_basic', '2025-01-01', '2026-06-27');


CREATE MATERIALIZED VIEW IF NOT EXISTS analyse_ban_history_daily_base
WITH (timescaledb.continuous) AS
SELECT
    -- 1. 按天进行时间分组
    time_bucket(INTERVAL '1 day', populate_time) AS bucket,
    peer_client_name,
    peer_id,
    -- 针对第 6 项需求：在聚合时直接将 IP 转换为对应的网络号 (CIDR)
    CASE
        WHEN family(peer_ip) = 4 THEN set_masklen(peer_ip::cidr, 24)
        WHEN family(peer_ip) = 6 THEN set_masklen(peer_ip::cidr, 52)
        END AS peer_ip_cidr,
    torrent_id,
    module_name,
    userapps_id,

    -- 基础统计量
    COUNT(*) AS ban_count,
    SUM(to_peer_traffic) AS total_to_peer_traffic,
    SUM(from_peer_traffic) AS total_from_peer_traffic
FROM ban_history
GROUP BY bucket, peer_client_name, peer_id, peer_ip_cidr, torrent_id, module_name, userapps_id
    WITH NO DATA;

ALTER MATERIALIZED VIEW analyse_ban_history_daily_base SET (timescaledb.compress = true);

SELECT add_continuous_aggregate_policy('analyse_ban_history_daily_base',
                                       start_offset => INTERVAL '3 days',    -- 考虑到可能有延迟数据，每次重刷最近3天
                                       end_offset => INTERVAL '1 hour',      -- 不刷新最近1小时（保证性能）
                                       schedule_interval => INTERVAL '1 day'  -- 每天执行一次
       );

CREATE OR REPLACE VIEW analyse_ban_history_daily_report AS
WITH
-- 1~3. 计算每日大盘总和
daily_summary AS (
    SELECT
        bucket,
        SUM(ban_count) AS total_ban_count,
        SUM(total_to_peer_traffic) AS total_to_peer_traffic,
        SUM(total_from_peer_traffic) AS total_from_peer_traffic
    FROM analyse_ban_history_daily_base
    GROUP BY bucket
),
-- 4. 当日封禁热门 peer_client_name
top_client AS (
    SELECT DISTINCT ON (bucket) bucket, peer_client_name AS top_peer_client_name
    FROM analyse_ban_history_daily_base GROUP BY bucket, peer_client_name ORDER BY bucket, SUM(ban_count) DESC
),
-- 5. 当日封禁热门 peer_id
top_peer AS (
    SELECT DISTINCT ON (bucket) bucket, peer_id AS top_peer_id
    FROM analyse_ban_history_daily_base GROUP BY bucket, peer_id ORDER BY bucket, SUM(ban_count) DESC
),
-- 6. 当日封禁热门 peer_ip (CIDR)
top_ip AS (
    SELECT DISTINCT ON (bucket) bucket, peer_ip_cidr AS top_peer_ip_cidr
    FROM analyse_ban_history_daily_base GROUP BY bucket, peer_ip_cidr ORDER BY bucket, SUM(ban_count) DESC
),
-- 7. 当日封禁热门 torrent_id
top_torrent AS (
    SELECT DISTINCT ON (bucket) bucket, torrent_id AS top_torrent_id
    FROM analyse_ban_history_daily_base GROUP BY bucket, torrent_id ORDER BY bucket, SUM(ban_count) DESC
),
-- 8. 当日封禁热门 module_name
top_module AS (
    SELECT DISTINCT ON (bucket) bucket, module_name AS top_module_name
    FROM analyse_ban_history_daily_base GROUP BY bucket, module_name ORDER BY bucket, SUM(ban_count) DESC
),
-- 9. 提交数据最多的 userapps_id (按 to_peer_traffic + from_peer_traffic 总流量算，或单指记录数？这里以总流量最大为例)
top_userapp AS (
    SELECT DISTINCT ON (bucket) bucket, userapps_id AS top_userapps_id
    FROM analyse_ban_history_daily_base
    GROUP BY bucket, userapps_id
    ORDER BY bucket, SUM(total_to_peer_traffic + total_from_peer_traffic) DESC
)
-- 将所有指标整合成一张扁平的每日报表
SELECT
    s.bucket AS date,
    s.total_ban_count AS "1.每日封禁记录数量",
    s.total_to_peer_traffic AS "2.每日所有的_to_peer_traffic_总和",
    s.total_from_peer_traffic AS "3.每日所有的_from_peer_traffic_总和",
    c.top_peer_client_name AS "4.当日封禁热门_peer_client_name",
    p.top_peer_id AS "5.当日封禁热门_peer_id",
    t_ip.top_peer_ip_cidr AS "6.当日封禁热门_peer_ip_CIDR",
    t.top_torrent_id AS "7.当日封禁热门_torrent_id",
    m.top_module_name AS "8.当日封禁热门_module_name",
    u.top_userapps_id AS "9.提交流量最多的_userapps_id"
FROM daily_summary s
    LEFT JOIN top_client c ON s.bucket = c.bucket
    LEFT JOIN top_peer p ON s.bucket = p.bucket
    LEFT JOIN top_ip t_ip ON s.bucket = t_ip.bucket
    LEFT JOIN top_torrent t ON s.bucket = t.bucket
    LEFT JOIN top_module m ON s.bucket = m.bucket
    LEFT JOIN top_userapp u ON s.bucket = u.bucket;



CALL refresh_continuous_aggregate('analyse_ban_history_daily_base', '2025-01-01', '2026-06-27');

