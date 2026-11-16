CREATE TABLE public.data_export_jobs (
    id uuid PRIMARY KEY,
    user_id bigint NOT NULL,
    category varchar(24) NOT NULL CHECK (category IN ('BANS', 'CLIENTS', 'SWARM', 'TRAFFIC', 'SUBMISSIONS')),
    filters jsonb NOT NULL,
    language varchar(8) NOT NULL,
    request_ip inet NOT NULL,
    user_agent text,
    requested_at timestamptz NOT NULL,
    quota_day date NOT NULL,
    status varchar(12) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'READY', 'FAILED', 'EXPIRED')),
    max_rows integer NOT NULL CHECK (max_rows > 0),
    max_bytes bigint NOT NULL CHECK (max_bytes > 0),
    retention_hours integer NOT NULL CHECK (retention_hours > 0),
    lease_until timestamptz,
    finished_at timestamptz,
    expires_at timestamptz,
    row_count integer,
    file_bytes bigint,
    truncated boolean NOT NULL DEFAULT FALSE,
    download_digest text,
    files_removed_at timestamptz
);

CREATE INDEX data_export_quota_idx ON public.data_export_jobs (user_id, quota_day)
    WHERE status <> 'FAILED';
CREATE INDEX data_export_pending_idx ON public.data_export_jobs (status, requested_at)
    WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX data_export_cleanup_idx ON public.data_export_jobs (status, expires_at)
    WHERE files_removed_at IS NULL;

INSERT INTO public.app_config_entries
    (config_key, config_value, value_type, config_group, source, sensitive, hot_reload)
SELECT 'sparkle.exports.' || name, value, 'string', 'sparkle.exports', 'sql-default', FALSE, TRUE
FROM (VALUES
    ('max-rows', '1000000'),
    ('max-file-mib', '15360'),
    ('retention-hours', '72'),
    ('download-mbps', '300'),
    ('daily-slots', '5'),
    ('pool-mib', '102400'),
    ('prompt-zh', $zh$欢迎使用 Sparkle3 BTN 数据导出功能。
请您注意，根据服务条款，BTN 收集数据仅供学习、研究、分析等学术用途。您不得将其用于直接商业用途。

每个用户每天最多可导出 {export_slots_max} 次数据，您今日还剩 {export_slots_available} 次导出机会可用。
每次导出数据最多可导出 {export_rows_max} 条数据，或者 {export_filesize_max} MiB 大小（压缩后），超出的部分将会被强制截断。$zh$),
    ('prompt-en', $en$Welcome to Sparkle3 BTN data export.
Under the terms of service, BTN data is for academic purposes such as learning, research, and analysis. You may not use it for direct commercial purposes.

Each user may request up to {export_slots_max} exports per day. You have {export_slots_available} remaining today.
Each export is limited to {export_rows_max} records or {export_filesize_max} MiB after compression. Results beyond either limit will be truncated.$en$)
) AS defaults(name, value)
ON CONFLICT (config_key) DO NOTHING;
