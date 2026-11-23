ALTER TABLE public.ban_submission_replay_daily ADD COLUMN last_replay_at timestamptz;

CREATE TABLE public.ban_replay_application_suspensions (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    app_id bigint NOT NULL REFERENCES public.userapp(id) ON DELETE CASCADE,
    owner_id bigint NOT NULL REFERENCES public."user"(id) ON DELETE CASCADE,
    suspended_at timestamptz NOT NULL,
    last_replay_at timestamptz NOT NULL,
    replay_batches bigint NOT NULL,
    replay_logs bigint NOT NULL,
    batch_limit bigint NOT NULL,
    log_limit bigint NOT NULL,
    mail_status text NOT NULL DEFAULT 'PENDING'
        CHECK (mail_status IN ('PENDING', 'SENT', 'SKIPPED', 'FAILED')),
    mail_attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX ban_replay_suspensions_app_replay_idx
    ON public.ban_replay_application_suspensions (app_id, last_replay_at DESC);
CREATE INDEX ban_replay_suspensions_owner_idx
    ON public.ban_replay_application_suspensions (owner_id);
CREATE INDEX ban_replay_suspensions_pending_mail_idx
    ON public.ban_replay_application_suspensions (next_attempt_at, id) WHERE mail_status = 'PENDING';

INSERT INTO public.app_config_entries (config_key, config_value, value_type, config_group, source, hot_reload)
VALUES
    ('sparkle.ping.sync-banhistory.replay-suspension.batch-detection-enabled', 'false', 'boolean', 'sparkle.ping', 'sql-default', true),
    ('sparkle.ping.sync-banhistory.replay-suspension.batch-limit', '150', 'number', 'sparkle.ping', 'sql-default', true),
    ('sparkle.ping.sync-banhistory.replay-suspension.log-detection-enabled', 'false', 'boolean', 'sparkle.ping', 'sql-default', true),
    ('sparkle.ping.sync-banhistory.replay-suspension.log-limit', '12000', 'number', 'sparkle.ping', 'sql-default', true),
    ('sparkle.ping.sync-banhistory.replay-suspension.email-subject', 'Sparkle BTN 应用已停用 / Application suspended', 'string', 'sparkle.ping', 'sql-default', true),
    ('sparkle.ping.sync-banhistory.replay-suspension.email-body', '{{recipientName}}，您好：

您的 BTN 应用 {{appId}} 因重传超过上限，已停止接收上报。该应用仍可获取规则和查询数据。
最近 {{windowDays}} 个 UTC 日捕捉到 {{replayBatches}} 次重传，共 {{replayLogs}} 条重传日志。
请检查客户端为何反复提交已上报的封禁日志。处理后可联系管理员恢复上报。

Your BTN application {{appId}} has been suspended from submitting data because it exceeded a replay limit.
The application can still fetch rules and query data.
In the last {{windowDays}} UTC days, {{replayBatches}} submissions contained {{replayLogs}} replayed logs.
Check why the client is resending previously reported bans, then contact an administrator to restore submissions.', 'string', 'sparkle.ping', 'sql-default', true)
ON CONFLICT (config_key) DO NOTHING;
