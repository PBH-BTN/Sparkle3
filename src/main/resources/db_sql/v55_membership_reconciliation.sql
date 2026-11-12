-- Reconciliation and audit support for provider transaction snapshots.

CREATE TABLE IF NOT EXISTS public.member_platform_sync_runs (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    pages_fetched INTEGER NOT NULL DEFAULT 0,
    records_seen INTEGER NOT NULL DEFAULT 0,
    inserted_count INTEGER NOT NULL DEFAULT 0,
    updated_count INTEGER NOT NULL DEFAULT 0,
    unchanged_count INTEGER NOT NULL DEFAULT 0,
    missing_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    CONSTRAINT member_platform_sync_runs_status_check
        CHECK (status IN ('RUNNING', 'COMPLETED', 'INCOMPLETE', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS member_platform_sync_runs_provider_time_idx
    ON public.member_platform_sync_runs (provider, started_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS member_platform_sync_runs_active_unique_idx
    ON public.member_platform_sync_runs (provider)
    WHERE status = 'RUNNING';

ALTER TABLE public.member_transactions
    ADD COLUMN IF NOT EXISTS remote_present BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS first_seen_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_reconciled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_sync_run_id BIGINT,
    ADD COLUMN IF NOT EXISTS last_changed_at TIMESTAMPTZ;

UPDATE public.member_transactions
SET first_seen_at = COALESCE(first_seen_at, synced_at, now()),
    last_reconciled_at = COALESCE(last_reconciled_at, now())
WHERE first_seen_at IS NULL OR last_reconciled_at IS NULL;

CREATE INDEX IF NOT EXISTS member_transactions_reconciliation_idx
    ON public.member_transactions (
        provider, remote_present, last_sync_run_id, occurred_at DESC
    );

CREATE TABLE IF NOT EXISTS public.member_transaction_audit_events (
    id BIGSERIAL PRIMARY KEY,
    sync_run_id BIGINT
        REFERENCES public.member_platform_sync_runs (id) ON DELETE SET NULL,
    provider VARCHAR(32) NOT NULL,
    external_transaction_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    changed_fields JSONB,
    previous_data JSONB,
    current_data JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT member_transaction_audit_event_type_check
        CHECK (event_type IN ('CREATED', 'UPDATED', 'MISSING_REMOTE', 'RESTORED_REMOTE'))
);

CREATE INDEX IF NOT EXISTS member_transaction_audit_provider_time_idx
    ON public.member_transaction_audit_events (
        provider, created_at DESC, external_transaction_id
    );
