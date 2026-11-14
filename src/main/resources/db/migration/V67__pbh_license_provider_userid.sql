-- Preserve the generic account value, falling back to the legacy Afdian identifier.
UPDATE public.pbh_licenses
SET external_user_id = afdian_user_id
WHERE external_user_id IS NULL AND provider = 'AFDIAN' AND afdian_user_id IS NOT NULL;

ALTER TABLE public.pbh_licenses RENAME COLUMN external_user_id TO userid;
ALTER TABLE public.pbh_licenses DROP COLUMN afdian_user_id;
