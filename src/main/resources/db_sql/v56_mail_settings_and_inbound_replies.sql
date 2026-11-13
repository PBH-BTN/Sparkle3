ALTER TABLE public.user_messages
    ADD COLUMN IF NOT EXISTS reply_token UUID;

CREATE UNIQUE INDEX IF NOT EXISTS user_messages_reply_token_idx
    ON public.user_messages (reply_token)
    WHERE reply_token IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.mail_inbound_messages (
    id BIGSERIAL PRIMARY KEY,
    mailbox VARCHAR(320) NOT NULL,
    uid VARCHAR(512) NOT NULL,
    message_id TEXT,
    from_address VARCHAR(320),
    reply_token UUID,
    source_message_id BIGINT REFERENCES public.user_messages (id) ON DELETE SET NULL,
    result_message_id BIGINT REFERENCES public.user_messages (id) ON DELETE SET NULL,
    status VARCHAR(24) NOT NULL,
    error TEXT,
    received_at TIMESTAMPTZ,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT mail_inbound_messages_mailbox_uid_unique UNIQUE (mailbox, uid)
);

CREATE INDEX IF NOT EXISTS mail_inbound_messages_status_processed_idx
    ON public.mail_inbound_messages (status, processed_at DESC);

INSERT INTO public.app_config_entries (
    config_key, config_value, value_type, config_group, source,
    sensitive, hot_reload
)
VALUES
    ('sparkle.mail.smtp.enabled', 'false', 'boolean', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.smtp.host', '', 'string', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.smtp.port', '587', 'number', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.smtp.username', '', 'string', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.smtp.password', '', 'string', 'sparkle.mail', 'sql-default', true, true),
    ('sparkle.mail.smtp.security', 'STARTTLS', 'string', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.smtp.auth', 'true', 'boolean', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.smtp.connection-timeout-seconds', '10', 'number', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.smtp.read-timeout-seconds', '15', 'number', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.smtp.write-timeout-seconds', '15', 'number', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.smtp.from-address', '', 'string', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.smtp.from-name', 'Sparkle BTN', 'string', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.pop3.enabled', 'false', 'boolean', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.pop3.host', '', 'string', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.pop3.port', '995', 'number', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.pop3.username', '', 'string', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.pop3.password', '', 'string', 'sparkle.mail', 'sql-default', true, true),
    ('sparkle.mail.pop3.security', 'SSL', 'string', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.pop3.reply-address', '', 'string', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.pop3.poll-interval-seconds', '60', 'number', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.pop3.connection-timeout-seconds', '15', 'number', 'sparkle.mail', 'sql-default', false, true),
    ('sparkle.mail.pop3.max-messages-per-poll', '100', 'number', 'sparkle.mail', 'sql-default', false, true)
ON CONFLICT (config_key) DO NOTHING;
