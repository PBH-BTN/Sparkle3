CREATE TABLE IF NOT EXISTS public.navigation_items (
    id VARCHAR(128) PRIMARY KEY,
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    label_zh VARCHAR(100) NOT NULL,
    label_en VARCHAR(100) NOT NULL,
    href VARCHAR(2048) NOT NULL,
    icon VARCHAR(32) NOT NULL DEFAULT 'link',
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    open_in_new_tab BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT
);

CREATE INDEX IF NOT EXISTS navigation_items_order_idx
    ON public.navigation_items (enabled, sort_order);
