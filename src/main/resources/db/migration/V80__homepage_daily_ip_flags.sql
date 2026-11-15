CREATE TABLE homepage_ip_flag_state (
    day date NOT NULL,
    ip inet NOT NULL,
    observed integer NOT NULL,
    published integer NOT NULL DEFAULT 0,
    PRIMARY KEY (day, ip)
);

SELECT create_hypertable('homepage_ip_flag_state', 'day',
    chunk_time_interval => INTERVAL '1 day', create_default_indexes => FALSE);

CREATE INDEX homepage_ip_flag_pending_idx ON homepage_ip_flag_state (day, ip)
    WHERE observed <> published;

CREATE TABLE homepage_ip_flag_daily (
    day date NOT NULL,
    metric smallint NOT NULL,
    ip_count bigint NOT NULL,
    PRIMARY KEY (day, metric)
);

SELECT create_hypertable('homepage_ip_flag_daily', 'day',
    chunk_time_interval => INTERVAL '1 day', create_default_indexes => FALSE);

-- Seed each UTC day independently using the last-observed-time index.
DO $bootstrap$
DECLARE
    first_day date;
    last_day date;
    target_day date;
    state_cutoff date := (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 14;
BEGIN
    SELECT (min(last_time_seen) AT TIME ZONE 'UTC')::date,
           (max(last_time_seen) AT TIME ZONE 'UTC')::date
    INTO first_day, last_day
    FROM swarm_tracker;

    FOR target_day IN SELECT first_day + n FROM generate_series(0, last_day - first_day) AS n
    LOOP
        WITH observations AS MATERIALIZED (
            SELECT host(peer_ip)::inet AS ip, bit_or(
                CASE WHEN flags IS NULL OR flags !~ '[^[:space:]]' THEN 1
                     ELSE 1
                           | ((strpos(flags, 'I') > 0)::integer << 1)
                           | ((strpos(flags, 'd') > 0)::integer << 2)
                           | ((strpos(flags, 'D') > 0)::integer << 3)
                           | ((strpos(flags, 'u') > 0)::integer << 4)
                           | ((strpos(flags, 'U') > 0)::integer << 5)
                           | ((strpos(flags, 'K') > 0)::integer << 6)
                           | ((strpos(flags, '?') > 0)::integer << 7)
                           | ((strpos(flags, 'O') > 0)::integer << 8)
                           | ((strpos(flags, 'H') > 0)::integer << 9)
                           | ((strpos(flags, 'X') > 0)::integer << 10)
                           | ((strpos(flags, 'L') > 0)::integer << 11)
                           | ((flags !~ '[HXL]')::integer << 12)
                           | ((strpos(flags, 'E') > 0)::integer << 13)
                           | ((strpos(flags, 'e') > 0)::integer << 14)
                           | ((strpos(flags, 'P') > 0)::integer << 15)
                           | ((strpos(flags, 'P') = 0)::integer << 16)
                END
            ) AS mask
            FROM swarm_tracker
            WHERE last_time_seen >= (target_day::timestamp AT TIME ZONE 'UTC')
              AND last_time_seen < ((target_day + 1)::timestamp AT TIME ZONE 'UTC')
            GROUP BY host(peer_ip)::inet
        ), state_seed AS (
            INSERT INTO homepage_ip_flag_state (day, ip, observed, published)
            SELECT target_day, ip, mask, mask
            FROM observations
            WHERE target_day >= state_cutoff
        )
        INSERT INTO homepage_ip_flag_daily (day, metric, ip_count)
        SELECT target_day, metric, sum(ip_count)::bigint
        FROM (SELECT mask, count(*) AS ip_count FROM observations GROUP BY mask) AS mask_counts
        CROSS JOIN generate_series(0, 16) AS metric
        WHERE (mask & (1 << metric)) <> 0
        GROUP BY metric;
    END LOOP;
END
$bootstrap$;

INSERT INTO public.app_config_entries
    (config_key, config_value, value_type, config_group, source, sensitive, hot_reload)
VALUES
    ('sparkle.homepage-statistics-refresh.modules.ip-flags.enabled',
        'true', 'boolean', 'sparkle.homepage-statistics-refresh', 'sql-default', false, true),
    ('sparkle.homepage-statistics-refresh.modules.ip-flags.refresh-interval-minutes',
        '1', 'number', 'sparkle.homepage-statistics-refresh', 'sql-default', false, true)
ON CONFLICT (config_key) DO NOTHING;
