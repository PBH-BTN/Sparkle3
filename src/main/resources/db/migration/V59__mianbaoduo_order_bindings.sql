-- Mianbaoduo exposes no buyer identity, so each claimed order is its own
-- binding. Allow several order claims per Sparkle user on that provider only.
ALTER TABLE public.member_platform_bindings
    DROP CONSTRAINT IF EXISTS member_platform_bindings_user_unique;

CREATE UNIQUE INDEX IF NOT EXISTS member_platform_bindings_provider_user_unique_idx
    ON public.member_platform_bindings (provider, btn_user_id)
    WHERE provider <> 'MIANBAODUO';
