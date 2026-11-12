-- Community group catalog and GitHub organization team invitations.

CREATE TABLE IF NOT EXISTS public.community_groups (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(128) NOT NULL,
    group_type VARCHAR(16) NOT NULL,
    title_zh VARCHAR(255) NOT NULL,
    title_en VARCHAR(255) NOT NULL,
    description_zh TEXT NOT NULL DEFAULT '',
    description_en TEXT NOT NULL DEFAULT '',
    url VARCHAR(2048),
    github_organization VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT,
    CONSTRAINT community_groups_slug_unique UNIQUE (slug),
    CONSTRAINT community_groups_type_check
        CHECK (group_type IN ('GITHUB', 'LINK')),
    CONSTRAINT community_groups_github_org_check
        CHECK (
            (group_type = 'GITHUB' AND github_organization IS NOT NULL)
            OR (group_type = 'LINK' AND github_organization IS NULL)
        )
);

CREATE INDEX IF NOT EXISTS community_groups_enabled_order_idx
    ON public.community_groups (enabled, sort_order, id);

CREATE TABLE IF NOT EXISTS public.community_group_github_teams (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL
        REFERENCES public.community_groups (id) ON DELETE CASCADE,
    team_slug VARCHAR(100) NOT NULL,
    team_name VARCHAR(255) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT community_group_github_teams_unique
        UNIQUE (group_id, team_slug)
);

CREATE INDEX IF NOT EXISTS community_group_github_teams_group_order_idx
    ON public.community_group_github_teams (group_id, sort_order, id);

CREATE TABLE IF NOT EXISTS public.community_group_invite_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES public."user" (id) ON DELETE CASCADE,
    group_id BIGINT NOT NULL
        REFERENCES public.community_groups (id) ON DELETE CASCADE,
    github_login VARCHAR(255) NOT NULL,
    result VARCHAR(32) NOT NULL,
    message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS community_group_invite_events_user_idx
    ON public.community_group_invite_events (user_id, created_at DESC);

INSERT INTO public.app_config_entries (
    config_key, config_value, value_type, config_group, source,
    sensitive, hot_reload
)
VALUES
    (
        'sparkle.github-community.personal-access-token',
        '',
        'string',
        'sparkle.github-community',
        'sql-default',
        TRUE,
        TRUE
    ),
    (
        'sparkle.github-community.api-base-url',
        'https://api.github.com',
        'string',
        'sparkle.github-community',
        'sql-default',
        FALSE,
        TRUE
    )
ON CONFLICT (config_key) DO NOTHING;
