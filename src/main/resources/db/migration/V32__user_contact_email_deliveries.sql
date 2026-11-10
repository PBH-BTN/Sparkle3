CREATE TABLE IF NOT EXISTS public.user_contact_email_deliveries (
    id BIGSERIAL PRIMARY KEY,
    sender_user_id BIGINT NOT NULL,
    sender_nickname VARCHAR(255),
    sender_email VARCHAR(255),
    recipient_user_id BIGINT NOT NULL,
    recipient_nickname VARCHAR(255),
    recipient_email VARCHAR(255),
    subject VARCHAR(255) NOT NULL,
    sender_ip VARCHAR(64),
    sender_user_agent TEXT,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS user_contact_email_sender_sent_idx
    ON public.user_contact_email_deliveries (sender_user_id, sent_at DESC);

CREATE INDEX IF NOT EXISTS user_contact_email_recipient_sent_idx
    ON public.user_contact_email_deliveries (recipient_user_id, sent_at DESC);
