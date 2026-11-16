ALTER TABLE public."user"
    ADD COLUMN roles text[] NOT NULL DEFAULT ARRAY['user'],
    ADD COLUMN supporter_auto_granted_at timestamptz,
    ADD COLUMN supporter_revoked_at timestamptz,
    ADD CONSTRAINT user_roles_valid CHECK (
        roles @> ARRAY['user']
        AND roles <@ ARRAY['user', 'supporter', 'moderator', 'admin']
        AND array_position(roles, NULL) IS NULL
    );

UPDATE public."user"
SET roles = ARRAY['user', CASE lower(trim(role))
    WHEN 'role_admin' THEN 'admin'
    WHEN 'role_moderator' THEN 'moderator'
    ELSE lower(trim(role))
END]
WHERE lower(trim(role)) IN ('admin', 'role_admin', 'moderator', 'role_moderator', 'supporter');

UPDATE public."user" u
SET roles = ARRAY['user'], supporter_revoked_at = COALESCE(d.completed_at, now())
FROM public.user_account_deletion_request d
WHERE d.user_id = u.id AND d.status = 'COMPLETED';

ALTER TABLE public."user" DROP COLUMN role;

INSERT INTO public.app_config_entries (
    config_key, config_value, value_type, config_group, source, sensitive, hot_reload
)
VALUES (
    'sparkle.memberships.supporter-threshold-cny', '0', 'string', 'sparkle.memberships',
    'sql-default', FALSE, TRUE
)
ON CONFLICT (config_key) DO NOTHING;
