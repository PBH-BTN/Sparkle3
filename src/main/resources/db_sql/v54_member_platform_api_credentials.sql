-- Configure how administrator API credentials are sent to each provider.

ALTER TABLE public.member_platform_configs
    ADD COLUMN IF NOT EXISTS auth_header VARCHAR(128) NOT NULL DEFAULT 'Authorization',
    ADD COLUMN IF NOT EXISTS auth_scheme VARCHAR(64) NOT NULL DEFAULT 'Bearer';

UPDATE public.member_platform_configs
SET auth_header = 'Authorization'
WHERE auth_header IS NULL OR trim(auth_header) = '';

UPDATE public.member_platform_configs
SET auth_scheme = 'Bearer'
WHERE auth_scheme IS NULL;
