-- Per-field privacy preferences replace the legacy three-level privacy model.
-- The legacy user.privacy_level column is retained only for migration compatibility.

CREATE TABLE IF NOT EXISTS public.user_privacy_settings (
    user_id BIGINT PRIMARY KEY REFERENCES public."user" (id) ON DELETE CASCADE,
    avatar_visibility VARCHAR(16) NOT NULL DEFAULT 'BLURRED',
    username_visibility VARCHAR(16) NOT NULL DEFAULT 'BLURRED',
    points_visibility VARCHAR(16) NOT NULL DEFAULT 'SHOW',
    receive_user_messages BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT user_privacy_avatar_visibility_check
        CHECK (avatar_visibility IN ('ORIGINAL', 'BLURRED', 'HIDDEN')),
    CONSTRAINT user_privacy_username_visibility_check
        CHECK (username_visibility IN ('FULL', 'BLURRED')),
    CONSTRAINT user_privacy_points_visibility_check
        CHECK (points_visibility IN ('SHOW', 'HIDDEN'))
);

INSERT INTO public.user_privacy_settings (
    user_id, avatar_visibility, username_visibility,
    points_visibility, receive_user_messages
)
SELECT
    id,
    CASE privacy_level
        WHEN 0 THEN 'ORIGINAL'
        WHEN 3 THEN 'HIDDEN'
        ELSE 'BLURRED'
    END,
    CASE privacy_level WHEN 0 THEN 'FULL' ELSE 'BLURRED' END,
    CASE
        WHEN privacy_level IN (0, 1) THEN 'SHOW'
        WHEN privacy_level IS NULL THEN 'SHOW'
        ELSE 'HIDDEN'
    END,
    privacy_level IS DISTINCT FROM 3
FROM public."user"
ON CONFLICT (user_id) DO NOTHING;

CREATE INDEX IF NOT EXISTS user_privacy_settings_avatar_idx
    ON public.user_privacy_settings (avatar_visibility);

CREATE TABLE IF NOT EXISTS public.user_notification_preferences (
    user_id BIGINT NOT NULL REFERENCES public."user" (id) ON DELETE CASCADE,
    category VARCHAR(64) NOT NULL,
    inbox_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, category)
);

CREATE TABLE IF NOT EXISTS public.user_messages (
    id BIGSERIAL PRIMARY KEY,
    dispatch_id UUID NOT NULL,
    broadcast BOOLEAN NOT NULL DEFAULT FALSE,
    sender_user_id BIGINT REFERENCES public."user" (id) ON DELETE SET NULL,
    sender_kind VARCHAR(16) NOT NULL DEFAULT 'USER',
    sender_display_name VARCHAR(255) NOT NULL,
    recipient_user_id BIGINT NOT NULL REFERENCES public."user" (id) ON DELETE CASCADE,
    category VARCHAR(64) NOT NULL DEFAULT 'DIRECT_MESSAGE',
    subject VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    inbox_visible BOOLEAN NOT NULL DEFAULT TRUE,
    email_requested BOOLEAN NOT NULL DEFAULT FALSE,
    email_forced BOOLEAN NOT NULL DEFAULT FALSE,
    email_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
    email_error TEXT,
    email_sent_at TIMESTAMPTZ,
    sender_ip INET,
    sender_user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS user_messages_recipient_created_idx
    ON public.user_messages (recipient_user_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS user_messages_recipient_unread_idx
    ON public.user_messages (recipient_user_id, created_at DESC)
    WHERE deleted_at IS NULL AND read_at IS NULL AND inbox_visible = TRUE;

CREATE INDEX IF NOT EXISTS user_messages_sender_day_idx
    ON public.user_messages (sender_user_id, created_at)
    WHERE sender_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS user_messages_dispatch_idx
    ON public.user_messages (dispatch_id);

CREATE INDEX IF NOT EXISTS user_messages_email_pending_idx
    ON public.user_messages (email_status, created_at)
    WHERE email_status = 'PENDING' AND email_requested = TRUE;
