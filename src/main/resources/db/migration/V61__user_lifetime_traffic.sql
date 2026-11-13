-- Schema only. Historical backfill intentionally omitted.
ALTER TABLE public.user_privacy_settings
    ADD COLUMN lifetime_traffic_visibility VARCHAR(16) NOT NULL DEFAULT 'HIDDEN'
        CHECK (lifetime_traffic_visibility IN ('SHOW', 'HIDDEN'));

CREATE TABLE public.user_lifetime_traffic (
    user_id BIGINT PRIMARY KEY REFERENCES public."user" (id) ON DELETE CASCADE,
    sent_self NUMERIC NOT NULL DEFAULT 0 CHECK (sent_self >= 0),
    received_self NUMERIC NOT NULL DEFAULT 0 CHECK (received_self >= 0),
    sent_ack NUMERIC NOT NULL DEFAULT 0 CHECK (sent_ack >= 0),
    received_ack NUMERIC NOT NULL DEFAULT 0 CHECK (received_ack >= 0),
    tracked_since TIMESTAMPTZ NOT NULL,
    history_partial BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A hash avoids retaining another copy of peer addresses and downloader names.
-- Include the client session's first_seen time so a new counter epoch starts at zero.
CREATE TABLE public.user_lifetime_traffic_checkpoint (
    user_id BIGINT NOT NULL REFERENCES public."user" (id) ON DELETE CASCADE,
    session_key UUID NOT NULL,
    uploaded BIGINT NOT NULL,
    downloaded BIGINT NOT NULL,
    PRIMARY KEY (user_id, session_key)
);

INSERT INTO public.user_lifetime_traffic (user_id, tracked_since, history_partial)
SELECT u.id, now(), TRUE FROM public."user" u
WHERE NOT EXISTS (SELECT 1 FROM public.user_account_deletion_request d
                  WHERE d.user_id = u.id AND d.status = 'COMPLETED');
