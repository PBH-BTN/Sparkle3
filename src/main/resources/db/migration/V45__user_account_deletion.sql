-- Self-service account deletion with a seven-day cooling-off period.
-- The request row remains after completion so account lifecycle state is auditable
-- without retaining the original user identifiers.

CREATE TABLE IF NOT EXISTS public.user_account_deletion_request (
    user_id BIGINT PRIMARY KEY,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    cooling_ends_at TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(64),
    last_error TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS user_account_deletion_due_idx
    ON public.user_account_deletion_request (cooling_ends_at)
    WHERE status = 'PENDING';

-- Completed account relations retain the requested GitHub UID/name tombstones.
-- Multiple deleted accounts legitimately share the UID value 0, while active
-- GitHub identities must remain unique.
DROP INDEX IF EXISTS public.user_rel_unique_idx;

CREATE UNIQUE INDEX IF NOT EXISTS user_rel_active_unique_idx
    ON public.user_rel (platform, platform_user_id)
    WHERE platform_user_id <> '0';
